package com.altapay.proxit;

public class RawHttpHeader
{
	private String key;
	private String value;
	
	public RawHttpHeader()
	{
		// For unmarchaling
	}
	
	public RawHttpHeader(String key, String value)
	{
		this.key = key;
		this.value = value;
	}

	public String getKey()
	{
		return key;
	}

	public void setKey(String key)
	{
		this.key = key;
	}

	public String getValue()
	{
		return value;
	}

	public void setValue(String value)
	{
		this.value = value;
	}

	public boolean matches(String key)
	{
		return this.key.toLowerCase().equals(key.toLowerCase());
	}
	
	public String toString()
	{
		return key+": "+value;
	}
}
