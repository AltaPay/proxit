package com.altapay.proxit;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
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
	private LinkedList<RawHttpHeader> headers = new LinkedList<>();
	private byte[] body;
	public enum MessageType {
		REQUEST,
		RESPONSE
	}
	private MessageType type = MessageType.REQUEST;
	private HttpExchange httpExchange;
	private RawHttpMessage response;
	private String httpProtocolVersion = "HTTP/1.1";
	private int responseCode = -1;
	private String responseMessage;
	private String requestHttpMethod;
	private String requestPath;
	private boolean alreadyHandled = false;
	
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

	public void addHeader(String key, String value)
	{
		headers.add(new RawHttpHeader(key, value));
	}
	
	public void setHeaders(LinkedList<RawHttpHeader> headers)
	{
		this.headers = headers;
	}
	
	public LinkedList<RawHttpHeader> getHeaders()
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
			context.createMarshaller().marshal(this, out);
			System.out.println("\n==== xml sent");
			context.createMarshaller().marshal(this, System.out);
			System.out.println("\n==== xml sent\n");
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
		byte[] body = ("<html>\n"
				+ "<head>\n"
				+ "	<title>Error</title>\n"
				+ "</head>\n"
				+ "<body>\n"
				+ "	<p>We experienced an error trying to proxy your request to the developer in question (error message: "+error+")</p>\n"
				+ "</body>\n"
				+ "</html>").getBytes();
		RawHttpMessage response = new RawHttpMessage();
		response.setId(id);
		response.setMessageType(MessageType.RESPONSE);
		//response.addHeader("HTTP/1.1 404 File Not Found");
		response.setResponseCode(404);
		response.setResponseMessage("File Not Found");
		response.addHeader("Server","AltaPay DevProxy/2015.10.13 (Ubuntu)");
		response.addHeader("Content-Length",""+body.length);
		response.addHeader("Connection","close");
		response.addHeader("Content-Type","text/html");
		
		response.setBody(body);
		
		return response;
	}

	public boolean isContentTypeText()
	{
		for(RawHttpHeader h : getHeaders())
		{
			if(h.matches("Content-Type"))
			{
				if(h.getValue().toLowerCase().contains("text"))
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
	
	public synchronized void handleResponse()
	{
		System.out.println("\n====== handleResponse(start: "+httpExchange.getRequestURI()+")");
		try
		{
			if(alreadyHandled)
			{
				throw new RuntimeException("Already handled this request");
			}
			else
			{
				System.out.println(response.getHttpResponseLine());
				// TODO: Deal with response not being null, because we timed out
				byte[] bytes = response.getBody();
				
				int responseCode = response.getResponseCode();
				for(RawHttpHeader h : response.getHeaders())
				{
					if(!h.matches("Content-Length"))
					{
						System.out.println(h);
						httpExchange.getResponseHeaders().set(h.getKey(), h.getValue());
					}
					else
					{
						System.out.println("(skipped) "+h);
					}
				}
	
				if(responseCode == 100)
				{
					httpExchange.sendResponseHeaders(responseCode, -1);
				}
				else
				{
					httpExchange.sendResponseHeaders(responseCode, bytes == null ? -1 : bytes.length);
					if(bytes != null)
					{
						System.out.println("[body of "+bytes.length+" bytes]");
						//System.out.println(new String(bytes));
						OutputStream os = httpExchange.getResponseBody();
						os.write(bytes);
						os.close();
					}
				}
				httpExchange.close();
			}
		}
		catch(Exception e)
		{
			// Not sure what we should do
			e.printStackTrace(System.out);
		}
		finally
		{
			System.out.println("====== handleResponse(end: "+httpExchange.getRequestURI()+")\n");
			alreadyHandled = true;
		}
	}

	@XmlTransient
	public String getHttpResponseLine()
	{
		return httpProtocolVersion+" "+responseCode+" "+responseMessage;
	}
	
	@XmlTransient
	public String getHttpRequestLine()
	{
		return requestHttpMethod+" "+requestPath+" "+httpProtocolVersion;
	}


	public int getResponseCode()
	{
		return responseCode;
	}

	public void setResponseCode(int responseCode)
	{
		this.responseCode = responseCode;
	}

	public String getRequestHttpMethod()
	{
		return requestHttpMethod;
	}

	public void setRequestHttpMethod(String requestHttpMethod)
	{
		this.requestHttpMethod = requestHttpMethod;
	}

	public String getRequestPath()
	{
		return requestPath;
	}

	public void setRequestPath(String requestPath)
	{
		this.requestPath = requestPath;
	}

	public void setHeader(String key, String value)
	{
		removeHeader(key);
		addHeader(key, value);
	}

	private void removeHeader(String key)
	{
		ArrayList<RawHttpHeader> toBeRemoved = new ArrayList<>();
		for(RawHttpHeader h : headers)
		{
			if(h.matches(key))
			{
				toBeRemoved.add(h);
			}
		}
		for(RawHttpHeader h : toBeRemoved)
		{
			headers.remove(h);
		}
	}

	public RawHttpMessage copy()
	{
		RawHttpMessage copy = new RawHttpMessage();
		copy.setId(getId());
		copy.setConnectionId(getConnectionId());
		for(RawHttpHeader h : getHeaders())
		{
			copy.addHeader(h.getKey(), h.getValue());
		}
		copy.setBody(getBody());
		copy.setMessageType(getMessageType());
		copy.setHttpExchange(getHttpExchange());
		//private RawHttpMessage response;
		//private String httpProtocolVersion = "HTTP/1.1";
		copy.setHttpProtocolVersion(getHttpProtocolVersion());
		copy.setResponseCode(getResponseCode());
		copy.setResponseMessage(getResponseMessage());
		copy.setRequestHttpMethod(getRequestHttpMethod());
		copy.setRequestPath(getRequestPath());
		
		return copy;
	}

	public String getResponseMessage()
	{
		return responseMessage;
	}

	public void setResponseMessage(String responseMessage)
	{
		this.responseMessage = responseMessage;
	}

	public String getHeader(String key)
	{
		for(RawHttpHeader h : headers)
		{
			if(h.matches(key))
			{
				return h.getValue();
			}
		}
		return null;
	}

	public void addHeader(RawHttpHeader header)
	{
		headers.add(header);
	}

	@XmlTransient
	public int getContentLength()
	{
		String contentLength = getHeader("Content-Length");
		return contentLength == null ? 0 : Integer.parseInt(contentLength);
	}

	public String getHttpProtocolVersion()
	{
		return httpProtocolVersion;
	}

	public void setHttpProtocolVersion(String httpProtocolVersion)
	{
		this.httpProtocolVersion = httpProtocolVersion;
	}
}
