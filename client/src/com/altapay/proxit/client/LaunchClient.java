package com.altapay.proxit.client;

public class LaunchClient
{
	public static void main(String[] args) throws Exception
	{
		ProxitClientContainer container = ProxitClientContainer.initialize();
		
		ProxitClient client = container.getProxitClient((short) 7089, (short) 8089, "localhost");
		
		client.start();
	}

}
