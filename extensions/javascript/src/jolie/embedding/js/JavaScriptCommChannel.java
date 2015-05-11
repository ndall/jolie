/***************************************************************************
 *   Copyright (C) 2008-2015 by Fabrizio Montesi <famontesi@gmail.com>     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie.embedding.js;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.Invocable;
import javax.script.ScriptException;
import jolie.Interpreter;
import jolie.js.JsUtils;
import jolie.net.CommChannel;
import jolie.net.CommMessage;
import jolie.net.PollableCommChannel;
import jolie.runtime.Value;
import jolie.runtime.typing.Type;

/**
 * @author Fabrizio Montesi
 * 
 * TODO: this shouldn't be polled
 */
public class JavaScriptCommChannel extends CommChannel implements PollableCommChannel
{
	private final Invocable invocable;
	private final Map< Long, CommMessage > messages = new ConcurrentHashMap< Long, CommMessage >();
	private final Object json;
	
	public JavaScriptCommChannel( Invocable invocable, Object json )
	{
		this.invocable = invocable;
		this.json = json;
	}

	@Override
	public CommChannel createDuplicate()
	{
		return new JavaScriptCommChannel( invocable, json );
	}

	protected void sendImpl( CommMessage message )
		throws IOException
	{
		Object returnValue = null;
		try {
			StringBuilder builder = new StringBuilder();
			JsUtils.valueToJsonString( message.value(), true, Type.UNDEFINED, builder );
			Object param = invocable.invokeMethod( json, "parse", builder.toString() );
			returnValue = invocable.invokeFunction( message.operationName(), param );
		} catch( ScriptException e ) {
			throw new IOException( e );
		} catch( NoSuchMethodException e ) {
			throw new IOException( e );
		}
		
		CommMessage response;
		if ( returnValue != null ) {
			Value value = Value.create();
			
			if ( returnValue instanceof Value ) {
				value.refCopy( (Value)returnValue );
			} else {
				try {
					Object s = invocable.invokeMethod( json, "stringify", returnValue );
					JsUtils.parseJsonIntoValue( new StringReader( (String)s ), value, true );
				} catch( ScriptException e ) {
					// TODO: do something here, maybe encode an internal server error
				} catch( NoSuchMethodException e ) {
					// TODO: do something here, maybe encode an internal server error
				}
				
				value.setValue( returnValue );
			}
			
			response = new CommMessage(
				message.id(),
				message.operationName(),
				message.resourcePath(),
				value,
				null
			);
		} else {
			response = CommMessage.createEmptyResponse( message );
		}
		
		messages.put( message.id(), response );
	}
	
	protected CommMessage recvImpl()
		throws IOException
	{
		throw new IOException( "Unsupported operation" );
	}

	/* protected CommMessage recvImpl()
		throws IOException
	{
		CommMessage ret = null;
		synchronized( messages ) {
			while( messages.isEmpty() ) {
				try {
					messages.wait();
				} catch( InterruptedException e ) {}
			}
			ret = messages.remove( 0 );
		}
		if ( ret == null ) {
			throw new IOException( "Unknown exception occurred during communications with a Java Service" );
		}
		return ret;
	}
	*/
	
	@Override
	public CommMessage recvResponseFor( CommMessage request )
		throws IOException
	{
		return messages.remove( request.id() );
	}

	@Override
	protected void disposeForInputImpl()
		throws IOException
	{
		Interpreter.getInstance().commCore().registerForPolling( this );
	}

	protected void closeImpl()
	{}

	public boolean isReady()
	{
		return( !messages.isEmpty() );
	}
}
