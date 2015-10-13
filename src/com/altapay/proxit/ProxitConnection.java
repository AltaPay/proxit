package com.altapay.proxit;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.util.UUID;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.altapay.proxit.RawHttpMessage.MessageType;

public class ProxitConnection
{
	private ProxitConnectionHandler server;
	private UUID id;
	private Socket socket;
	private InputThread inputThread;

	public ProxitConnection(ProxitConnectionHandler server, UUID id, Socket socket)
	{
		this.server = server;
		this.id = id;
		this.socket = socket;
	}
	
	public UUID getId()
	{
		return id;
	}

	public void start() throws IOException
	{
		inputThread = new InputThread(new BufferedReader(new InputStreamReader(new BufferedInputStream(socket.getInputStream()))));
		new Thread(inputThread, "Conn("+id+")").start();
	}
	
	public void stop()
	{
		try
		{
			if(socket != null)
			{
				Socket tmp = socket;
				socket = null;
				tmp.close();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			server.removeConnection(this);
		}
	}
	
	private class InputThread implements Runnable
	{
		private BufferedReader in;

		public InputThread(BufferedReader in)
		{
			this.in = in;
		}

		@Override
		public void run()
		{
			Unmarshaller unmarchaller;
			try
			{
				unmarchaller = JAXBContext.newInstance(RawHttpMessage.class).createUnmarshaller();
				while(!socket.isClosed() && !socket.isInputShutdown())
				{
					String line = in.readLine();
					if(line != null)
					{
						RawHttpMessage message = (RawHttpMessage)unmarchaller.unmarshal(new StringReader(line.trim()));
						message.setConnectionId(id);
						
						if(message.getMessageType() == MessageType.REQUEST)
						{
							server.proxyRequest(ProxitConnection.this, message);
						}
						else if(message.getMessageType() == MessageType.RESPONSE)
						{
							// Find the requester and send back the response
							server.proxyResponse(message);
						}
						else
						{
							throw new RuntimeException("Could not determine the type ("+message.getMessageType()+") of HttpMessage");
						}
					}
					else
					{
						break;
					}
				}
			}
			catch (JAXBException e1)
			{
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			stop();
		}
	}

	public synchronized void sendMessage(RawHttpMessage response) throws IOException
	{
		response.writeXmlBytes(socket.getOutputStream());
	}

	public Socket getSocket()
	{
		return socket;
	}
}
