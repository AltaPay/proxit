package com.altapay.proxit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.google.common.io.ByteStreams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class HttpProxyServer implements HttpHandler
{
	private short listenPort;
	private HttpServer server;
	private Map<UUID,RawHttpMessage> requests = new HashMap<>();
	private ResponseSocketProvider socketProvider;

	public HttpProxyServer(short listenPort, ResponseSocketProvider socketProvider)
	{
		this.listenPort = listenPort;
		this.socketProvider = socketProvider;
	}
	
	public void start() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress(listenPort), 25);
		server.createContext("/", this);
		server.start();
		System.out.println("Listening on port "+listenPort);
	}

	@Override
	public void handle(HttpExchange httpExchange) throws IOException
	{
		// Read one request
		//RawHttpMessage request = RawHttpSender.readHttpMessage(clientSocket, RawHttpMessage.MessageType.REQUEST);
		RawHttpMessage request = new RawHttpMessage();
		request.setId(UUID.randomUUID());
		request.setMessageType(RawHttpMessage.MessageType.REQUEST);
		request.setHttpExchange(httpExchange);
		
		// Read the header
		request.setRequestHttpMethod(httpExchange.getRequestMethod());
		request.setRequestPath(httpExchange.getRequestURI().toString());
		for(Entry<String,List<String>> entry : httpExchange.getRequestHeaders().entrySet())
		{
			for(String v : entry.getValue())
			{
				request.addHeader(entry.getKey(), v);
			}
		}
		request.setHeader("Connection", "close");
		request.setHeader("Accept-Encoding", "identity");
		
		// Read the body
		byte[] body = ByteStreams.toByteArray(httpExchange.getRequestBody());
		request.setBody(body);
		
		synchronized (request)
		{
			try
			{
				Socket socket = socketProvider.getSocket(request);
				if(socket != null && !socket.isClosed())
				{
					requests.put(request.getId(), request);
					request.writeXmlBytes(socket.getOutputStream());
					
					try
					{
						request.wait(1000 * 60); // We only wait 60 seconds.
						request.handleResponse();
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
				else
				{
					request.setResponse(RawHttpMessage.get404Response(request.getId(), "Could not find client"));
					request.handleResponse();
				}
			}
			catch(Throwable t)
			{
				request.setResponse(RawHttpMessage.get404Response(request.getId(), t.getMessage()));
				request.handleResponse();
			}
		}
	}
	
	public synchronized RawHttpMessage removeRequest(UUID id)
	{
		return requests.remove(id);
	}
}
