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
		
		// Listen for clients
		new Thread(this).start();
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
			
			request = rewriteRequest(request);
			
			System.out.println("Request: "+request);
			
			RawHttpMessage response = RawHttpSender.sendRequest(s, request);
			
			System.out.println("Response: "+response);
			
			// TODO: send this back to the client, such that it may return it
			client.sendMessage(response);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	private RawHttpMessage rewriteRequest(RawHttpMessage orig) throws UnsupportedEncodingException
	{
		RawHttpMessage request = new RawHttpMessage();
		request.setId(orig.getId());
		
		String body = "";
		for(String s : orig.getBody().split("&"))
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
		request.setBody(body);
		
		for(String h : orig.getHeaders())
		{
			if(h.startsWith("Content-Length: "))
			{
				request.addHeader("Content-Length: "+body.length());
			}
			else if(h.startsWith("Host: "))
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
			System.out.println("Connecting to: https://"+config.getGatewayHost()+":443");
			return factory.createSocket(config.getGatewayHost(), 443);
		}
		else
		{
			System.out.println("Connecting to: http://"+config.getGatewayHost()+": 80");
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
				System.out.println(h);
				String[] parts = h.split(" ");
				// POST /cb/f5c9f3e5-80ed-4d15-baf4-d0d9fc5e9a0b?http://shopdomain.url/pensiopayment/form.php HTTP/1.1
				//          |----------------------------------|
				UUID id = UUID.fromString(parts[1].substring(4, 4+32+4));
				
				ProxitConnection client = clients.get(id);
				return client.getSocket();
			}
		}
		throw new RuntimeException("Cannot find a POST or GET line in the headers");
	}

	@Override
	public void proxyResponse(RawHttpMessage response)
	{
		RawHttpMessage request = receiver.removeRequest(response.getId());
		RawHttpSender.sendResponse(request.getSocket(), response);
	}
}
