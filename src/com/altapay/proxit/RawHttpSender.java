package com.altapay.proxit;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.altapay.proxit.RawHttpMessage.MessageType;
import com.google.common.io.ByteStreams;

public class RawHttpSender
{

	public static RawHttpMessage sendRequest(Socket s, RawHttpMessage request) throws IOException
	{
		writeHttpBytes(s.getOutputStream(), request);
		
		RawHttpMessage response = readHttpMessage(s, MessageType.RESPONSE);
		response.setId(request.getId());
		
		return response;
	}
	
	public static void sendResponse(Socket s, RawHttpMessage request)
	{
		try
		{
			writeHttpBytes(s.getOutputStream(), request);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public static RawHttpMessage readHttpMessage(Socket clientSocket, RawHttpMessage.MessageType type) throws IOException
	{
		//byte[] responseBytes = ByteStreams.toByteArray(clientSocket.getInputStream());
		BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		
		RawHttpMessage request = new RawHttpMessage();
		request.setMessageType(type);
		request.setSocket(clientSocket);
		
		// Read the header
		int bodyLength = -1;
		String line;
		do
		{
			line = in.readLine();
			if(line == null)
			{
				throw new IOException("Connection closed before we read the whole HTTP request: "+request.getHeaders());
			}
			else if(line.startsWith("Content-Length: "))
			{
				bodyLength = Integer.parseInt(line.substring(15).trim());
				request.addHeader(line);
			}
			else if(line.startsWith("Connection: "))
			{
				request.addHeader("Connection: close");
			}
			else if(line.startsWith("Accept-Encoding: "))
			{
				request.addHeader("Accept-Encoding: identity");
			}
			else if(line.equals("Expect: 100-continue"))
			{
				// Tell it to continue (to get the body)
				clientSocket.getOutputStream().write("HTTP/1.1 100 Continue\n\n".getBytes());
			}
			else
			{
				request.addHeader(line);
			}
		}
		while(!"".equals(line));
		
		
		
		// Read the body
		if(bodyLength > -1)
		{
			char[] body = new char[bodyLength];
			int read = 0;
			while(read < bodyLength)
			{
				int r = in.read(body, read, bodyLength-read);
				if(r != -1)
				{
					read += r;
				}
				else
				{
					// The stream ended
					break;
				}
			}
			request.setBody(new String(body).getBytes());
		}

		return request;
	}

	private static void writeHttpBytes(OutputStream out, RawHttpMessage message) throws IOException
	{
		for(String h : message.getHeaders())
		{
			if(h.startsWith("Content-Length: "))
			{
				h = "Content-Length: "+message.getBody().length;
			}
			out.write((h+"\r\n").getBytes());
		}
		
		if(message.getBody() != null)
		{
			out.write(message.getBody());
		}
	}
}
