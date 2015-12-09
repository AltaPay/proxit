package com.altapay.proxit.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;

import com.altapay.proxit.HTMLUrlRewriter;
import com.altapay.proxit.HttpProxyClient;
import com.altapay.proxit.HttpProxyServer;
import com.altapay.proxit.ProxitConnection;
import com.altapay.proxit.ProxitConnectionHandler;
import com.altapay.proxit.RawHttpHeader;
import com.altapay.proxit.RawHttpMessage;
import com.altapay.proxit.ResponseSocketProvider;

public class ProxitClient implements ResponseSocketProvider, ProxitConnectionHandler
{

	private short listenPort;
	private short serverPort;
	private String serverHost;
	private ProxitConnection serverCon;
	private HttpProxyServer httpRequestReceiver;
	private Socket serverSocket;

	public ProxitClient(short listenPort, short serverPort, String serverHost)
	{
		this.listenPort = listenPort;
		this.serverPort = serverPort;
		this.serverHost = serverHost;
	}
	
	public void start() throws UnknownHostException, IOException
	{
		// Connect to server
		serverSocket = new Socket(serverHost, serverPort);
		serverCon = new ProxitConnection(this, null, serverSocket);
		serverCon.start();
		System.out.println("Connected to "+serverHost+":"+serverPort);
		
		// Listen for incoming requests
		httpRequestReceiver = new HttpProxyServer(listenPort, this);
		httpRequestReceiver.start();
	}

	@Override
	public Socket getSocket(RawHttpMessage message)
	{
		// We always forward requests to the server
		return serverSocket;
	}

	@Override
	public void removeConnection(ProxitConnection connection)
	{
		// We do not need to do anything here
	}

	@Override
	public void proxyRequest(ProxitConnection connection, RawHttpMessage request)
	{
		try
		{
			// Open a connection to the destination host
			URL url = getUrlToCallbackTo(request);
			
			HttpURLConnection con = createSocketToDest(url);
			
			request = rewriteRequestForInside(request, url);

			RawHttpMessage response = HttpProxyClient.sendRequest(con, request);
			
			response = rewriteResponseFromTheInside(url, response);
			
			connection.sendMessage(response);
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.out);
			try
			{
				connection.sendMessage(RawHttpMessage.get404Response(request.getId(), t.getMessage()));
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}
		}
	}

	private RawHttpMessage rewriteResponseFromTheInside(URL url, RawHttpMessage orig)
	{
		RawHttpMessage response = orig.copy();

		HTMLUrlRewriter rewriter = new HTMLUrlRewriter();
		if(orig.getBody() != null)
		{
			if(orig.isContentTypeText())
			{
				response.setBody(rewriter.makeUrlsAbsolute(url, new StringBuffer(new String(orig.getBody()))).toString().getBytes());
			}
			else
			{
				response.setBody(orig.getBody());
			}
		}
		
		for(RawHttpHeader h : orig.getHeaders())
		{
			if(h.matches("Location"))
			{
				response.addHeader(h.getKey(), rewriter.createAbsoluteUrl(url, h.getValue()));
			}
			else
			{
				response.addHeader(h);
			}
		}
		
		return response;
	}
	
	private URL getUrlToCallbackTo(RawHttpMessage request)
	{
		String[] paths = request.getRequestPath().split("\\?", 2);
		if(paths.length != 2)
		{
			throw new RuntimeException("No ? in the URL: "+request.getRequestPath());
		}
		
		try
		{
			return new URL(paths[1]);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		throw new RuntimeException("Could not figure out where to post the callback on the dev machine: "+request.getRequestPath());
	}
	
	private HttpURLConnection createSocketToDest(URL url) throws UnknownHostException, IOException
	{
		return (HttpURLConnection)url.openConnection();
	}

	private RawHttpMessage rewriteRequestForInside(RawHttpMessage orig, URL url) throws UnsupportedEncodingException
	{
		RawHttpMessage request = orig.copy();
		
		request.setRequestPath(url.getPath()+(url.getQuery() == null ? "" : "?"+url.getQuery()));
		request.setHeader("Host", url.getHost()+(url.getPort() != -1 ? ":"+url.getPort() : ""));
		
		return request;
	}
	

	@Override
	public void proxyResponse(RawHttpMessage response)
	{
		RawHttpMessage request = httpRequestReceiver.removeRequest(response.getId());
		
		request.setResponse(response);
	}

}
