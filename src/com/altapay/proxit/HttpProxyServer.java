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
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

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
		request.addHeader(httpExchange.getRequestMethod()+" "+httpExchange.getRequestURI()+" HTTP/1.1");
		for(Entry<String, List<String>> entry : httpExchange.getRequestHeaders().entrySet())
		{
			System.out.println("entry:"+entry.getKey()+" ("+entry.getValue()+")");
			for(String s : entry.getValue())
			{
				if(entry.getKey().equals("Connection"))
				{
					request.addHeader("Connection: close");
				}
				else if(entry.getKey().equals("Accept-Encoding"))
				{
					request.addHeader("Accept-Encoding: identity");
				}
				else
				{
					request.addHeader(entry.getKey()+": "+s);
				}
			}
		}
		request.addHeader("");
		
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
				}
				else
				{
					RawHttpSender.sendResponse(request.getSocket(), RawHttpMessage.get404Response(request.getId(), "Could not find client"));
				}
			}
			catch(Throwable t)
			{
				RawHttpSender.sendResponse(request.getSocket(), RawHttpMessage.get404Response(request.getId(), t.getMessage()));
			}
		
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
	}
	
	public RawHttpMessage removeRequest(UUID id)
	{
		return requests.remove(id);
	}
}
