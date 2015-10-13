package com.altapay.proxit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class RawHttpReceiver
{
	private ServerSocket listenSocket;
	private Map<UUID,RawHttpMessage> requests = new HashMap<>();
	private ResponseSocketProvider socketProvider;

	public RawHttpReceiver(ServerSocket listenSocket, ResponseSocketProvider socketProvider)
	{
		this.listenSocket = listenSocket;
		this.socketProvider = socketProvider;
	}
	
	public void start()
	{
		new Thread(new InputThread(), "RawHttp("+listenSocket.getLocalPort()+")").start();
	}

	private class InputThread implements Runnable
	{
		@Override
		public void run()
		{
			while(!listenSocket.isClosed())
			{
				try
				{
					Socket clientSocket = listenSocket.accept();
					if(clientSocket != null)
					{
						// Read one request
						RawHttpMessage request = RawHttpSender.readHttpMessage(clientSocket, RawHttpMessage.MessageType.REQUEST);
						request.setId(UUID.randomUUID());
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
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			
		}
	}
	
	public RawHttpMessage removeRequest(UUID id)
	{
		return requests.remove(id);
	}
}
