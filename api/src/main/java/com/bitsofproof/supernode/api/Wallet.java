/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.api;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Wallet
{
	private final ExtendedKey master;
	private int nextKey;
	private List<Wallet> subs;
	private final List<Key> importedKeys = new ArrayList<Key> ();
	private final Map<String, Key> keyForAddress = new HashMap<String, Key> ();
	private final List<WalletListener> walletListener = new ArrayList<WalletListener> ();

	public Wallet (ExtendedKey master, int nextKey) throws ValidationException
	{
		this.master = master;
		this.nextKey = nextKey;
		for ( int i = 0; i < nextKey; ++i )
		{
			ExtendedKey k = getKey (i);
			keyForAddress.put (AddressConverter.toSatoshiStyle (k.getMaster ().getPublic (), k.getMaster ().getAddressFlag ()), k.getMaster ());
		}
	}

	public static Wallet createWallet (int addressFlag) throws ValidationException
	{
		SecureRandom random = new SecureRandom ();
		ECKeyPair master = ECKeyPair.createNew (true, addressFlag);
		byte[] chainCode = new byte[32];
		random.nextBytes (chainCode);
		ExtendedKey parent = new ExtendedKey (master, chainCode);
		return new Wallet (parent, 0);
	}

	public Wallet createSubWallet (int sequence) throws ValidationException
	{
		if ( sequence > nextKey )
		{
			throw new ValidationException ("Subwallets must use consecutive sequences");
		}
		if ( sequence == nextKey )
		{
			generateNextKey ();
		}
		if ( subs == null )
		{
			subs = new ArrayList<Wallet> ();
		}
		Wallet sub = new Wallet (getKey (sequence), 0);
		subs.add (sub);
		for ( WalletListener l : walletListener )
		{
			sub.addListener (l);
		}
		return sub;
	}

	public void addListener (WalletListener listener)
	{
		walletListener.add (listener);
		for ( Wallet sub : subs )
		{
			sub.addListener (listener);
		}
	}

	public ExtendedKey getKey (int sequence) throws ValidationException
	{
		if ( sequence > nextKey )
		{
			throw new ValidationException ("Sequence requested is higher than generated before");
		}
		return KeyGenerator.generateKey (master, sequence);
	}

	public ExtendedKey generateNextKey () throws ValidationException
	{
		ExtendedKey k = KeyGenerator.generateKey (master, nextKey++);
		String address = AddressConverter.toSatoshiStyle (k.getMaster ().getPublic (), k.getMaster ().getAddressFlag ());
		Key key = k.getMaster ();
		keyForAddress.put (address, key);
		notifyNewKey (key, address);
		return k;
	}

	private void notifyNewKey (Key k, String address)
	{
		for ( WalletListener l : walletListener )
		{
			l.notifyNewKey (address, k);
		}
	}

	public void importKey (Key k)
	{
		try
		{
			importedKeys.add (k.clone ());

			String address = AddressConverter.toSatoshiStyle (k.getPublic (), k.getAddressFlag ());
			keyForAddress.put (address, k);
			notifyNewKey (k, address);
		}
		catch ( CloneNotSupportedException e )
		{
		}
	}

	public Key getKeyForAddress (String address)
	{
		return keyForAddress.get (address);
	}

	public List<String> getAddresses (int addressFlag) throws ValidationException
	{
		List<String> addresses = new ArrayList<String> (keyForAddress.keySet ());
		if ( subs != null )
		{
			for ( Wallet sub : subs )
			{
				addresses.addAll (sub.getAddresses (addressFlag));
			}
		}
		return addresses;
	}

	public ExtendedKey getMaster ()
	{
		return master;
	}

	public int getNextKeySequence ()
	{
		return nextKey;
	}
}