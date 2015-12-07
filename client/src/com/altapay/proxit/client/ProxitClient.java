package com.altapay.proxit.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;

import com.altapay.proxit.HTMLUrlRewriter;
import com.altapay.proxit.ProxitConnection;
import com.altapay.proxit.ProxitConnectionHandler;
import com.altapay.proxit.RawHttpMessage;
import com.altapay.proxit.RawHttpReceiver;
import com.altapay.proxit.RawHttpSender;
import com.altapay.proxit.ResponseSocketProvider;

public class ProxitClient implements ResponseSocketProvider, ProxitConnectionHandler
{

	private short listenPort;
	private short serverPort;
	private String serverHost;
	private ProxitConnection serverCon;
	private RawHttpReceiver httpRequestReceiver;
	private Socket serverSocket;

	public ProxitClient(short listenPort, short serverPort, String serverHost)
	{
		this.listenPort = listenPort;
		this.serverPort = serverPort;
		this.serverHost = serverHost;
	}
	
	public void start() throws UnknownHostException, IOException
	{
		// Connect to server
		serverSocket = new Socket(serverHost, serverPort);
		serverCon = new ProxitConnection(this, null, serverSocket);
		serverCon.start();
		System.out.println("Connected to "+serverHost+":"+serverPort);
		
		// Listen for incoming requests
		ServerSocket listenSocket = new ServerSocket(listenPort);
		httpRequestReceiver = new RawHttpReceiver(listenSocket, this);
		httpRequestReceiver.start();
		System.out.println("Listening on port "+listenPort);
	}

	@Override
	public Socket getSocket(RawHttpMessage message)
	{
		// We always forward requests to the server
		return serverSocket;
	}

	@Override
	public void removeConnection(ProxitConnection connection)
	{
		// We do not need to do anything here
	}

	@Override
	public void proxyRequest(ProxitConnection connection, RawHttpMessage request)
	{
		try
		{
			// Open a connection to the destination host
			URL url = getUrlToCallbackTo(request);
			
			Socket s = createSocketToDest(url);
			
			request = rewriteRequestForInside(request, url);

			RawHttpMessage response = RawHttpSender.sendRequest(s, request);
			
			response = rewriteResponseFromTheInside(url, response);
			
			connection.sendMessage(response);
		}
		catch (Throwable t)
		{
			try
			{
				connection.sendMessage(RawHttpMessage.get404Response(request.getId(), t.getMessage()));
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}
		}
	}

	private RawHttpMessage rewriteResponseFromTheInside(URL url, RawHttpMessage orig)
	{
		RawHttpMessage response = new RawHttpMessage();
		response.setId(orig.getId());
		response.setConnectionId(orig.getConnectionId());
		response.setMessageType(orig.getMessageType());
		response.setSocket(orig.getSocket());

		HTMLUrlRewriter rewriter = new HTMLUrlRewriter();
		if(orig.isContentTypeText())
		{
			response.setBody(rewriter.makeUrlsAbsolute(url, new StringBuffer(new String(orig.getBody()))).toString().getBytes());
		}
		else
		{
			response.setBody(orig.getBody());
		}
		
		for(String h : orig.getHeaders())
		{
			if(h.startsWith("Location:"))
			{
				String[] parts = h.split(":", 2);
				response.addHeader("Location: "+rewriter.createAbsoluteUrl(url, parts[1].trim()));
			}
			else
			{
				response.addHeader(h);
			}
		}
		
		return response;
	}
	
	private URL getUrlToCallbackTo(RawHttpMessage request)
	{
		for(String h : request.getHeaders())
		{
			if(h.startsWith("POST ") || h.startsWith("GET "))
			{
				String[] parts = h.split(" ");
				String[] paths = parts[1].split("\\?", 2);
				
				try
				{
					return new URL(paths[1]);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
		throw new RuntimeException("Could not figure out where to post the callback on the dev machine");
	}
	
	private Socket createSocketToDest(URL url) throws UnknownHostException, IOException
	{
		boolean https = "https".equals(url.getProtocol());
		
		return new Socket(url.getHost(), url.getPort() == -1 ? (https ? 443 : 80) : url.getPort());
	}

	private RawHttpMessage rewriteRequestForInside(RawHttpMessage orig, URL url) throws UnsupportedEncodingException
	{
		RawHttpMessage request = new RawHttpMessage();
		request.setMessageType(orig.getMessageType());
		request.setId(orig.getId());
		request.setBody(orig.getBody());
		request.setConnectionId(orig.getConnectionId());
		
		
		for(String h : orig.getHeaders())
		{
			if(h.startsWith("POST ") || h.startsWith("GET "))
			{
				String[] parts = h.split(" ");
				request.addHeader(parts[0]+" "+url.getPath()+(url.getQuery() == null ? "" : "?"+url.getQuery())+" "+parts[2]);
			}
			else if(h.startsWith("Host: "))
			{
				request.addHeader("Host: "+url.getHost()+(url.getPort() != -1 ? ":"+url.getPort() : ""));
			}
			else
			{
				request.addHeader(h);
			}
		}
		
		return request;
	}
	

	@Override
	public void proxyResponse(RawHttpMessage response)
	{
		RawHttpMessage request = httpRequestReceiver.removeRequest(response.getId());
		RawHttpSender.sendResponse(request.getSocket(), response);
	}

}
