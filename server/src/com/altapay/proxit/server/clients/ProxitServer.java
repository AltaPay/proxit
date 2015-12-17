package com.altapay.proxit.server.clients;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.altapay.proxit.HttpProxyClient;
import com.altapay.proxit.HttpProxyServer;
import com.altapay.proxit.ProxitConnection;
import com.altapay.proxit.ProxitConnectionHandler;
import com.altapay.proxit.RawHttpMessage;
import com.altapay.proxit.ResponseSocketProvider;
import com.altapay.proxit.server.IProxitConfig;

public class ProxitServer implements Runnable, ResponseSocketProvider, ProxitConnectionHandler
{
	private ServerSocket socket;
	private Map<UUID,ProxitConnection> clients = new HashMap<>();
	private IProxitConfig config;
	private HttpProxyServer receiver;

	public ProxitServer(IProxitConfig config)
	{
		this.config = config;
	}
	
	public void start() throws UnknownHostException, IOException
	{
		// Open ports
		socket = new ServerSocket(config.getClientListenPort());
		
		// Listen for callbacks
		receiver = new HttpProxyServer(config.getCallbackListenPort(), this);
		receiver.start();
		
		// Listen for clients
		new Thread(this, "ProxitServer").start();
		
		System.out.println("Listen for clients on port: "+config.getClientListenPort());
	}
	
	
	@Override
	public void run()
	{
		try
		{
			Socket clientSocket;
			while((clientSocket = socket.accept()) != null)
			{
				addClient(clientSocket);
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	private synchronized void addClient(Socket clientSocket)
	{
		UUID id = UUID.randomUUID();
		ProxitConnection client = new ProxitConnection(this, id, clientSocket);
		clients.put(id, client);
		System.out.println("Added client: "+client.getId());
		try
		{
			client.start();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			removeConnection(client);
		}
	}

	@Override
	public void removeConnection(ProxitConnection client)
	{
		if(clients.get(client.getId()) != null)
		{
			clients.remove(client.getId());
			System.out.println("Removed client: "+client.getId());
			client.stop();
		}
	}

	@Override
	public void proxyRequest(ProxitConnection client, RawHttpMessage request)
	{
		try
		{
			// Open a connection to the destination host
			request = rewriteRequestForOutside(request);
			
			URL url = new URL(config.getGatewayHost()+request.getRequestPath());

			RawHttpMessage response = HttpProxyClient.sendRequest(url, request);
			
			client.sendMessage(response);
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.out);
			try
			{
				client.sendMessage(RawHttpMessage.get404Response(request.getId(), t.getMessage()));
			}
			catch (Throwable e)
			{
				e.printStackTrace(System.out);
			}
		}
	}
	
	@Override
	public void proxyResponse(RawHttpMessage response)
	{
		RawHttpMessage request = receiver.removeRequest(response.getId());
		// TODO: send this back to the client, such that it may return it
		ResourcesProxyRewriter rewriter = new ResourcesProxyRewriter();
		if(response.getBody() != null)
		{
			if(response.isContentTypeText())
			{
				StringBuffer b = rewriter.proxyResourceUrls(config.getCallbackBaseUrl()+"/cb/"+response.getConnectionId()+"?", new StringBuffer(new String(response.getBody(), response.getBodyCharset())));
				response.setBody(b.toString().getBytes());
			}
		}
		
		request.setResponse(response);
	}

	private RawHttpMessage rewriteRequestForOutside(RawHttpMessage orig) throws UnsupportedEncodingException
	{
		RawHttpMessage request = orig.copy();
		
		String body = "";
		if(orig.getBody() != null)
		{
			System.out.println("Body[before]: "+new String(orig.getBody()));
			for(String s : new String(orig.getBody()).split("&"))
			{
				String[] parts = s.split("=", 2);
				String key = URLDecoder.decode(parts[0], "utf-8");
				String value = URLDecoder.decode(parts.length > 1 ? parts[1] : "", "utf-8");
				if(key.startsWith("config[callback_") && !"".equals(value))
				{
					value = config.getCallbackBaseUrl()+"/cb/"+orig.getConnectionId()+"?"+value;
				}
				if(body.length() != 0)
				{
					body += "&";
				}
				body += URLEncoder.encode(key, "utf-8")+"="+URLEncoder.encode(value, "utf-8");
			}
			request.setBody(body.getBytes());
			System.out.println("Body[after]: "+new String(body.getBytes()));
		}

		request.setHeader("Host", config.getGatewayHost());
		
		return request;
	}

	@Override
	public Socket getSocket(RawHttpMessage message)
	{
		// POST /cb/f5c9f3e5-80ed-4d15-baf4-d0d9fc5e9a0b?http://shopdomain.url/pensiopayment/form.php HTTP/1.1
		//          |----------------------------------|
		UUID id = UUID.fromString(message.getRequestPath().substring(4, 4+32+4));
		
		ProxitConnection client = clients.get(id);
		if(client == null)
		{
			throw new RuntimeException("Client has disconnected");
		}
			
		return client.getSocket();
	}
}
