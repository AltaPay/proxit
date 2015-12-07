package com.altapay.proxit.server.clients;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import com.altapay.proxit.ProxitConnection;
import com.altapay.proxit.ProxitConnectionHandler;
import com.altapay.proxit.RawHttpMessage;
import com.altapay.proxit.RawHttpReceiver;
import com.altapay.proxit.RawHttpSender;
import com.altapay.proxit.ResponseSocketProvider;
import com.altapay.proxit.server.IProxitConfig;

public class ProxitServer implements Runnable, ResponseSocketProvider, ProxitConnectionHandler
{
	private ServerSocket socket;
	private Map<UUID,ProxitConnection> clients = new HashMap<>();
	private IProxitConfig config;
	private ServerSocket callbackSocket;
	private RawHttpReceiver receiver;

	public ProxitServer(IProxitConfig config)
	{
		this.config = config;
	}
	
	public void start() throws UnknownHostException, IOException
	{
		// Open ports
		callbackSocket = new ServerSocket(config.getCallbackListenPort());
		socket = new ServerSocket(config.getClientListenPort());
		
		// Listen for callbacks
		receiver = new RawHttpReceiver(callbackSocket, this);
		receiver.start();
		System.out.println("Listen for callback on port: "+config.getCallbackListenPort());
		
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
			Socket s = createSocketToDest();
			
			request = rewriteRequestForOutside(request);
			
			RawHttpMessage response = RawHttpSender.sendRequest(s, request);
			
			client.sendMessage(response);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public void proxyResponse(RawHttpMessage response)
	{
		RawHttpMessage request = receiver.removeRequest(response.getId());
		try
		{
			// TODO: send this back to the client, such that it may return it
			ResourcesProxyRewriter rewriter = new ResourcesProxyRewriter();
			if(response.isContentTypeText())
			{
				StringBuffer b = rewriter.proxyResourceUrls(config.getCallbackBaseUrl()+"/cb/"+response.getConnectionId()+"?", new StringBuffer(new String(response.getBody())));
				response.setBody(b.toString().getBytes());
			}
			
			RawHttpSender.sendResponse(request.getSocket(), response);
		}
		finally
		{
			try
			{
				request.getSocket().close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private RawHttpMessage rewriteRequestForOutside(RawHttpMessage orig) throws UnsupportedEncodingException
	{
		RawHttpMessage request = new RawHttpMessage();
		request.setId(orig.getId());
		
		String body = "";
		if(orig.getBody() != null)
		{
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
		}
		
		for(String h : orig.getHeaders())
		{
			if(h.startsWith("Host: "))
			{
				request.addHeader("Host: "+config.getGatewayHost());
			}
			else
			{
				request.addHeader(h);
			}
		}
		
		return request;
	}

	private Socket createSocketToDest() throws UnknownHostException, IOException
	{
		if(config.getGatewaySsl())
		{
			SSLSocketFactory factory=(SSLSocketFactory) SSLSocketFactory.getDefault();
			//System.out.println("Connecting to: https://"+config.getGatewayHost()+":443");
			return factory.createSocket(config.getGatewayHost(), 443);
		}
		else
		{
			//System.out.println("Connecting to: http://"+config.getGatewayHost()+":80");
			return new Socket(config.getGatewayHost(), 80);
		}
	}

	@Override
	public Socket getSocket(RawHttpMessage message)
	{
		for(String h : message.getHeaders())
		{
			if(h.startsWith("POST ") || h.startsWith("GET "))
			{
				String[] parts = h.split(" ");
				// POST /cb/f5c9f3e5-80ed-4d15-baf4-d0d9fc5e9a0b?http://shopdomain.url/pensiopayment/form.php HTTP/1.1
				//          |----------------------------------|
				UUID id = UUID.fromString(parts[1].substring(4, 4+32+4));
				
				ProxitConnection client = clients.get(id);
				if(client == null)
				{
					throw new RuntimeException("Client has disconnected");
				}
					
				return client.getSocket();
			}
		}
		throw new RuntimeException("Cannot find a POST or GET line in the headers");
	}
}
