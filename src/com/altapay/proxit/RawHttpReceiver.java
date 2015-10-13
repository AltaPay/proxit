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
						
						Socket socket = socketProvider.getSocket(request);
						if(socket != null)
						{
							requests.put(request.getId(), request);
							request.writeXmlBytes(socket.getOutputStream());
						}
						else
						{
							throw new RuntimeException("Could not find socket for "+request.getId());
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
