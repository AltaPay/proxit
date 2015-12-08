package com.altapay.proxit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import com.altapay.proxit.RawHttpMessage.MessageType;

public class RawHttpSender
{

	public static RawHttpMessage sendRequest(Socket s, RawHttpMessage request) throws IOException
	{
		writeHttpRequestBytes(s.getOutputStream(), request);
		
		RawHttpMessage response = readHttpResponse(s, MessageType.RESPONSE);
		response.setId(request.getId());
		
		return response;
	}
	
	public static RawHttpMessage readHttpResponse(Socket clientSocket, RawHttpMessage.MessageType type) throws IOException
	{
		System.out.println("\n======= readHttpResponse(start)");
		//byte[] responseBytes = ByteStreams.toByteArray(clientSocket.getInputStream());
		BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		
		RawHttpMessage request = new RawHttpMessage();
		request.setMessageType(type);
		//request.setSocket(clientSocket);
		
		// Read the header
		String line;
		do
		{
			line = in.readLine();
			System.out.println(line);
			if(line == null)
			{
				throw new IOException("Connection closed before we read the whole HTTP request: "+request.getHeaders());
			}
			else if(line.equals(""))
			{
				// Fine, end of headers
			}
			else if(line.startsWith("HTTP/1.1 100 Continue"))
			{
				// Swallow the next line (as this is empty)
				System.out.println(in.readLine());
			}
			else if(line.startsWith("HTTP/1.1 ") || line.startsWith("HTTP/1.0 "))
			{
				String[] parts = line.split(" ", 3);
				request.setHttpProtocolVersion(parts[0]);
				request.setResponseCode(Integer.parseInt(parts[1]));
				request.setResponseMessage(parts[2]);
			}
			else
			{
				String[] parts = line.split(": ", 2);
				request.addHeader(parts[0], parts[1]);
			}
		}
		while(!"".equals(line));
		//request.setHeader("Connection", "close");
		
		/*
		if(!foundAcceptEncoding)
		{
			request.getHeaders().remove(request.getHeaders().size()-1);
			request.addHeader("Accept-Encoding: identity");
			request.addHeader("");
		}
		*/
		
		
		
		// Read the body
		int bodyLength = request.getContentLength();
		if(bodyLength > 0)
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
			System.out.println("[Body: ("+bodyLength+"/"+request.getBody().length+" bytes)]");
		}

		System.out.println("======= readHttpResponse(end)\n");
		return request;
	}

	private static void writeHttpRequestBytes(OutputStream out, RawHttpMessage message) throws IOException
	{
		// TODO: Write HTTP/1.1 200 OK (or what ever)
		System.out.println("\n======= writeHttpRequestBytes(start)");
		System.out.println("message.getHttpRequestLine(): "+message.getHttpRequestLine());
		out.write((message.getHttpRequestLine()+"\r\n").getBytes());
		
		message.setHeader("Accept-Encoding", "identity");
		for(RawHttpHeader h : message.getHeaders())
		{
			if(h.matches("content-length"))
			{
				h.setValue(""+message.getBody().length);
			}
			System.out.println(h);
			out.write((h+"\r\n").getBytes());
		}
		out.write(("\r\n").getBytes());
		
		if(message.getBody() != null)
		{
			System.out.println();
			System.out.println("[Body: "+message.getBody().length+" bytes]");
			out.write(message.getBody());
		}
		System.out.println("======= writeHttpRequestBytes(end)\n");
	}
}
