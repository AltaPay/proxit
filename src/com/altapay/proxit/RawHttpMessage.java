package com.altapay.proxit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.sun.net.httpserver.HttpExchange;

@XmlRootElement(name="http")
public class RawHttpMessage
{
	private UUID id;
	private UUID connectionId;
	ArrayList<String> headers = new ArrayList<>();
	private byte[] body;
	public enum MessageType {
		REQUEST,
		RESPONSE
	}
	private MessageType type = MessageType.REQUEST;
	private Socket socket;
	private HttpExchange httpExchange;
	private RawHttpMessage response;
	
	public RawHttpMessage()
	{
	}
	
	public void setId(UUID id)
	{
		this.id = id;
	}

	public UUID getId()
	{
		return id;
	}

	public MessageType getMessageType()
	{
		return type;
	}

	public void setMessageType(MessageType type)
	{
		this.type = type;
	}

	public void addHeader(String header)
	{
		headers.add(header);
	}
	
	public void setHeaders(ArrayList<String> headers)
	{
		this.headers = headers;
	}
	
	public ArrayList<String> getHeaders()
	{
		return headers;
	}
	
	@XmlTransient
	public void setBody(byte[] body)
	{
		this.body = body;
	}
	
	public byte[] getBody()
	{
		return body;
	}
	
	@XmlTransient
	public Socket getSocket()
	{
		return socket;
	}

	public void setSocket(Socket socket)
	{
		this.socket = socket;
	}
	
	@XmlTransient
	public UUID getConnectionId()
	{
		return connectionId;
	}

	public void setConnectionId(UUID connectionId)
	{
		this.connectionId = connectionId;
	}
	
	
	public String getBase64Body()
	{
		return (body != null ? DatatypeConverter.printBase64Binary(body) : null);
	}

	public void setBase64Body(String body)
	{
		this.body = (body != null ? DatatypeConverter.parseBase64Binary(body) : null);
	}

	public void writeXmlBytes(OutputStream out)
	{
		try
		{
			JAXBContext context = JAXBContext.newInstance(RawHttpMessage.class);
			System.out.println(type+","+headers.size()+" headers"+(body != null ? ", "+body.length+" bytes of body" : ""));
			context.createMarshaller().marshal(this, out);
			out.write("\n".getBytes());
		}
		catch (JAXBException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public String toString()
	{
		return headers+(body != null ? "Body[length:"+body.length+"]" : "");
	}
	
	public static RawHttpMessage get404Response(UUID id, String error)
	{
		String body = "<html>\n"
				+ "<head>\n"
				+ "	<title>Error</title>\n"
				+ "</head>\n"
				+ "<body>\n"
				+ "	<p>We experienced an error trying to proxy your request to the developer in question (error message: "+error+")</p>\n"
				+ "</body>\n"
				+ "</html>";
		RawHttpMessage response = new RawHttpMessage();
		response.setId(id);
		response.setMessageType(MessageType.RESPONSE);
		response.addHeader("HTTP/1.1 404 File Not Found");
		response.addHeader("Server: AltaPay DevProxy/2015.10.13 (Ubuntu)");
		response.addHeader("Content-Length: "+body.length());
		response.addHeader("Connection: close");
		response.addHeader("Content-Type: text/html");
		response.addHeader("");
		
		response.setBody(body.getBytes());
		
		return response;
	}

	public boolean isContentTypeText()
	{
		for(String h : headers)
		{
			if(h.startsWith("Content-Type:"))
			{
				if(h.contains("text"))
					return true;
			}
		}
		return false;
	}

	@XmlTransient
	public HttpExchange getHttpExchange()
	{
		return httpExchange;
	}

	public void setHttpExchange(HttpExchange httpExchange)
	{
		this.httpExchange = httpExchange;
	}

	@XmlTransient
	public synchronized void setResponse(RawHttpMessage response)
	{
		this.response = response;
		notify();
	}
	
	public void handleResponse()
	{
		// TODO: Deal with response not being null, because we timed out
		byte[] bytes = response.getBody();
		
		int responseCode = -1;
		for(String h : response.getHeaders())
		{
			System.out.println("Header: "+h);
			if(h.startsWith("HTTP/1.1"))
			{
				// This is the first line
				// "HTTP/1.1 200 OK"
				String[] firstHeader = h.split(" ", 3);
				responseCode = Integer.parseInt(firstHeader[1]);
			}
			else if(h.length() == 0)
			{
				// Ignore
			}
			else
			{
				String[] header = h.split(": ", 2);
				httpExchange.getResponseHeaders().set(header[0], header[1]);
			}
		}
		try
		{
			if(responseCode == 100)
			{
				httpExchange.sendResponseHeaders(responseCode, -1);
			}
			else
			{
				httpExchange.sendResponseHeaders(responseCode, bytes == null ? -1 : bytes.length);
				if(bytes != null)
				{
					System.out.println(new String(bytes));
					OutputStream os = httpExchange.getResponseBody();
					os.write(bytes);
					os.close();
				}
			}
			//httpExchange.close();
		}
		catch(IOException e)
		{
			// Not sure what we should do
			e.printStackTrace();
		}
	}
}
