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
		new Thread(new InputThread()).start();
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
								write404Response(request, "Could not find client");
							}
						}
						catch(Throwable t)
						{
							write404Response(request, "Could not find client");
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

	public void write404Response(RawHttpMessage request, String error) throws IOException
	{
		String response =
			"HTTP/1.1 404 "+error+"\r\n"
			+ "Server: AltaPay DevProxy/2015.10.13 (Ubuntu)\r\n"
			+ "\r\n"
			+ "We experienced an error trying to proxy your request to the developer in question";
		request.getSocket().getOutputStream().write(response.getBytes());
		request.getSocket().close();
	}

}
