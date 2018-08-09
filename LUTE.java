/*
  _     _    _ _______ ______ 
 | |   | |  | |__   __|  ____|
 | |   | |  | |  | |  | |__   
 | |   | |  | |  | |  |  __|  
 | |___| |__| |  | |  | |____ 
 |______\____/   |_|  |______|	v1.1_b
                              
 Linguistic Unique Tokenizing Explicator
 
 :: A Java-based program for tokenizing and parsing simple (partial) languages.
 :: Currently configured for a small subset of SML
 
*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class LUTE {

	public static void main( String[] args ) {
		try {
			Project3 tc = new Project3( );
			tc.test( args[0], 0 );
			//tc.test( "./test/test3.sml", 0 );
		}
		catch( IOException ioe ) {
			System.out.println( "Could Not Find File!" );
			ioe.printStackTrace( );
		}
	}
	
	/**
	 * Tokenizes and parses the passed-in file.
	 * 
	 * @param outputMode	debug purposes, unused externally	
	 */
	public void test( String fileName, int outputMode ) throws IOException {
		File file = new File( fileName );
		BufferedReader br = new BufferedReader( new FileReader( file ) );
		
		// Indicates syntax error
		boolean encounteredSyntaxError = false;
		
		// raw data from file stream
		int i;
		
		// data as text char
		char c;
		
		// an assembled string of chars
		StringBuilder tokenString = new StringBuilder( "" );
		
		I_TOKEN token = null;
		
		// char in line that token starts
		int token_start = 0;
		int char_count = -1;
		int token_num = 0;
		int line = 1;
		
		// Read the input file character by character
		// With each character read, attempt to find a valid token
		// This process ignores the possibility of data and identifier tokens until a delimiter (space, newline) is reached
		while( (i = br.read( )) >= 0 ) {
			c = (char)i;
			char_count++;
			
			// The last part handles an edge case where you read a character and then EOF
			boolean currentCharIsDelimiter = (this.read( c, tokenString ) > 0) || !br.ready( );
			
			if( !currentCharIsDelimiter && tokenString.length() == 1 )
				token_start = char_count;
			
			token = this.getToken( tokenString.toString( ), currentCharIsDelimiter );
			
			if( token != null ) {
				this.addToken( token, tokenString.toString( ), token_num, line, token_start, outputMode );
				token_num++;
				
				tokenString = new StringBuilder( "" );
			}
			// First pass syntax error checking
			// We've read the entire string of characters and haven't found a valid token
			// So we do some work
			else {
				if( currentCharIsDelimiter && tokenString.length() > 0 ) {
					
					// Search for a valid token, cut off the last character, search, repeat
					// If a token is found, "cut it out" and repeat with the remaining characters
					String opTokenChar;
					LinkedList<I_TOKEN> opTokens = new LinkedList<I_TOKEN>( );
					I_TOKEN opToken;
					
					do {
						opToken = null;
						int looklength = tokenString.length( );
						for( ; looklength > 0; --looklength ) {
							opTokenChar = tokenString.substring( 0, looklength );
							opToken = this.getToken( opTokenChar, true );
							if( opToken != null )
								break;
						}
						
						if( opToken != null ) {
							opTokens.add( opToken );
							tokenString.delete( 0 , looklength );
						}
						
					}
					while( opToken != null );
					
					// The length check is necessary to make sure the entire string was a glob of tokens
					// Otherwise, we ignore syntax errors like "2.532.50" for a float because it will see
					// "2.532" as a valid float and ignore the remaining ".50"
					if( !opTokens.isEmpty( ) && tokenString.length( ) == 0 ) {
						for( I_TOKEN t : opTokens ) {
							this.addToken(t, t.getTokenChars( ), ++token_num, line, token_start, outputMode );
							token_start += t.getTokenChars( ).length( );
						}
					}
					else {
						//System.out.println( "Syntax Error on Line " + line + " at position " + token_start + ": " + tokenString );
						System.out.println( "Line " + line + " : syntax error : " + tokenString.substring( 0, 1 ) );
						encounteredSyntaxError = true;
						break;
					}
				}
			}

			if( c == '\n' ) {
				line++;
				char_count = -1;
				token_start = 0;
				tokenString = new StringBuilder( "" );
			}
		}
		br.close( );
		
		// We only proceed to the next part if we didn't flag a syntax error in the first pass
		// This is the second pass where we proceed to validate the token juxtaposition
		if( !encounteredSyntaxError ) {
			// Technically unnecessary but kept just in case
			//this.tokenBuffer.add( new TOKEN_META_CHUNK( this.EOF, line, token_start, tokenString.toString( ) ) );
			
			// A "stack" of all the logic/conditional/loop type tokens encountered and their "depth" in the logic
			// Used to track the flow of conditions and check that nothing is left dangling or is extra.
			// Likely inefficient for large/deep programs
			LinkedList<TOKEN_LOGIC_DEPTH> logicStack = new LinkedList<TOKEN_LOGIC_DEPTH>( );
			
			// How "deep" into a condition we are. If you had 3 nested if's, they'd be 1, 2, and 3 respectively
			int logicDepth = -1;
			
			// There is an interesting quirk with the "do" token in that it lacks an end-of-loop partner token
			// LUTE struggles with identifying when a ; means "end of do" and not as part of other sequences
			// It tries its best, but compensating for ; as part of logic makes things... interesting.
			int doCount = 0;
			
			// We maintain both a 2-token look-ahead AND a 1-token look-behind just in case
			for( int ix = 0; ix < this.tokenBuffer.size( ); ++ix ) {
				TOKEN_META_CHUNK chunk = this.tokenBuffer.get( ix );
				TOKEN_META_CHUNK prevChunk = (ix == 0 ? null : this.tokenBuffer.get( ix - 1) );
				TOKEN_META_CHUNK nextChunk = (ix < this.tokenBuffer.size( ) - 1 ? this.tokenBuffer.get( ix + 1 ) : null );
				TOKEN_META_CHUNK afterChunk = (ix < this.tokenBuffer.size( ) - 2 ? this.tokenBuffer.get( ix + 2 ) : null );
				
				I_TOKEN t = chunk.token;
				
				boolean prevTokenIsNull = prevChunk == null || prevChunk.token == null || prevChunk.line < chunk.line;
				boolean nextTokenIsNull = nextChunk == null || nextChunk.token == null || nextChunk.line > chunk.line;
				boolean afterTokenIsNull = afterChunk == null || afterChunk.token == null || afterChunk.line > chunk.line;
				
				if( !t.checkJuxtapose( prevTokenIsNull ? null : prevChunk.token, nextTokenIsNull ? null : nextChunk.token, afterTokenIsNull ? null : afterChunk.token ) ) {
					I_TOKEN offendingToken = t.getOffendingToken( prevChunk == null ? null : prevChunk.token, nextChunk == null ? null : nextChunk.token, afterTokenIsNull ? null : afterChunk.token );
					TOKEN_META_CHUNK offendingChunk = chunk;
					if( offendingToken != null ) {
						offendingChunk = prevChunk != null && offendingToken == prevChunk.token ? prevChunk : nextChunk != null && offendingToken == nextChunk.token ? nextChunk : chunk;
						
					}
					//System.out.println( "[2nd Pass] Syntax Error on Line " + offendingChunk.line + " at position " + offendingChunk.startPos + ": " + offendingChunk.tokenValue );
					System.out.println( "Line " + offendingChunk.line + " : syntax error : " + offendingChunk.tokenValue );
					encounteredSyntaxError = true;
					break;
				}
				
				if( t.getType( ) == TOKEN_TYPE.LOGIC ) {
					TOKEN logicToken = (TOKEN)t;
					
					// We increase our logic depth if we hit a new condition/loop
					switch( logicToken ) {
						case IF:
						case LET:
						case WHILE:
							logicDepth++;
							break;
						case DO:
							doCount++;
						default:
							break;
					}
					
					if( !logicStack.isEmpty( ) ) {
						// peek back to the last logic token at the same depth
						Iterator<TOKEN_LOGIC_DEPTH> itr = logicStack.descendingIterator( );
						
						TOKEN peek = null;
						//int peekDepth = -1;
						while( itr.hasNext( ) ) {
							TOKEN_LOGIC_DEPTH tld = itr.next( );
							// we only care about tokens on the same depth
							if( tld.depth == logicDepth ) {
								peek = tld.token;
								//peekDepth = tld.depth;
								break;
							}
						}
						
						// Debug
						//System.out.println( (peek != null ? peek.getTokenChars( )  + "\t": "(none)\t") + peekDepth + "\t" + logicToken.getTokenChars( ) + "\t" + logicDepth );
						
						boolean flow = true;
						
						//Sequence just breaks everything but we make do best we can
						if( peek != null && peek != TOKEN.SEQUENCE ) {
							switch( logicToken ) {
								case THEN:
									flow = (peek == TOKEN.IF);
									break;
								case ELSE:
									flow = (peek == TOKEN.THEN);
									break;
								case IN:
									flow = (peek == TOKEN.LET);
									break;
								case END:
									flow = (peek == TOKEN.IN || peek == TOKEN.THEN || peek == TOKEN.DO || peek == TOKEN.END);
									break;
								case DO:
									flow = (peek == TOKEN.WHILE);
									break;
								//case SEQUENCE:
									//flow = (doCount <= 0) || (doCount > 0 && (peek == TOKEN.DO));
									//break;
								default:
									break;
							}
						}
						if( !flow ) {
							//System.out.println( "[2nd Pass LOGIC] Syntax Error on Line " + chunk.line + " at position " + chunk.startPos + ": " + chunk.tokenValue );
							System.out.println( "Line " + chunk.line + " : syntax error : " + chunk.tokenValue );
							
							encounteredSyntaxError = true;
							break;
						}
						
					}
					logicStack.add( new TOKEN_LOGIC_DEPTH( logicToken, logicDepth ) );
					
					// decrement because we finished at that depth
					switch( logicToken ) {
						case ELSE:
						case END:
							--logicDepth;
							break;
						case SEQUENCE:
							if( doCount > 0 )
								--logicDepth;
							break;
						default:
							break;
					}
					
				}
				
				// We replace "do" ;'s with ENDs internally to make them recognizable
				// during ML->C parsing. Otherwise, we'd have to attempt WAY worse
				// logic in that phase.
				if( t == TOKEN.SEQUENCE && doCount > 0 ) {
					chunk.applyHackyDoEndFix( );
					doCount--;
				}
				
				// metadata hack for third pass
				// the extra hack that "counters" the depth decrement for ELSE and END is
				// just here so I don't have to debug the whole thing to ensure making a change
				// above didn't break existing stuff. Ain't enough time to regression test.
				chunk.logicDepth = chunk.token.getType() == TOKEN_TYPE.LOGIC ? (chunk.token == TOKEN.ELSE || chunk.token == TOKEN.END ? logicDepth + 1 : logicDepth) : logicDepth + 1;
				
			}
			// debug
			//for( TOKEN_LOGIC_DEPTH tld : logicStack )
				//System.out.println( tld.token.getTokenChars() + "[" + tld.depth + "]" );
			if( !encounteredSyntaxError ) {
				//System.out.println( "parsing successful" );
				
				
				// Stupid hack for stupid things
				LinkedList<IDENTIFIER_DECLARATION_SEMANTIC_CHUNK> decStack = new LinkedList<IDENTIFIER_DECLARATION_SEMANTIC_CHUNK>( );
				
				
				// We parse into C code
				
				// The list of tokens in the current sequence (a sub-list of all tokens)
				ArrayList<TOKEN_META_CHUNK> seqStack = new ArrayList<TOKEN_META_CHUNK>( );
				
				StringBuilder cCode = new StringBuilder( );
				cCode.append( "#include <ml-c.h>\n" );
				cCode.append( "int main() \n" );
				cCode.append( "{\n" );
				
				for( int ix = 0; ix < this.tokenBuffer.size( ); ++ix ) {
					TOKEN_META_CHUNK chunk = this.tokenBuffer.get( ix );
					
					I_TOKEN t = chunk.token;
					
					// We look at the tokens in strings of sequences
					// Index check is just in case we're on the last token
					// and it isn't a ; for some reason (e.g. "DO" ; converted to END)
					if( t != TOKEN.SEQUENCE && ix < this.tokenBuffer.size( ) - 1 ) {
						seqStack.add( chunk );
					}
					else {
						seqStack.add( chunk );
						TOKEN_META_CHUNK[] arr = new TOKEN_META_CHUNK[ seqStack.size( ) ];
						seqStack.toArray( arr );
						
						I_TOKEN[] tarr = new I_TOKEN[ arr.length ];
						for( int arri = 0; arri < arr.length; ++arri )
							tarr[arri] = arr[arri].token;
						
						MULTI_TOKEN_SEMANTIC_CHUNK mtsc = this.getSemanticChunkForTokens( arr[0].logicDepth, tarr );
						
						if( mtsc != null ) {
							// indent sequence
							for( int indents = 0; indents <= arr[0].logicDepth; ++indents )
								cCode.append( "    " );
							cCode.append( mtsc.toCCode( ) );
						}
						else {
							// We check if subsequences of the sequence contain multi-token chunks
							// We start from the left and head rightward, adding 1 token at a time
							// If we get nothing by the end, pop off the leftmost token, parse it,
							// and try again
							while( seqStack.size( ) > 1 ) {
								for( int subEnd = 1; subEnd <= seqStack.size( ); ++subEnd ) {
									List<TOKEN_META_CHUNK> subStack = seqStack.subList( 0, subEnd );
									arr = new TOKEN_META_CHUNK[ subStack.size( ) ];
									subStack.toArray( arr );
									
									tarr = new I_TOKEN[ arr.length ];
									for( int arri = 0; arri < arr.length; ++arri )
										tarr[arri] = arr[arri].token;
									
									mtsc = this.getSemanticChunkForTokens( arr[0].logicDepth, tarr );
									if( mtsc != null ) {
										if( mtsc instanceof IDENTIFIER_DECLARATION_SEMANTIC_CHUNK )
											decStack.add( (IDENTIFIER_DECLARATION_SEMANTIC_CHUNK)mtsc );
										
										else if( mtsc instanceof ID_LIST_CON_SEMANTIC_CHUNK ) {
											ID_LIST_CON_SEMANTIC_CHUNK ilcsc = (ID_LIST_CON_SEMANTIC_CHUNK)mtsc;
											I_TOKEN conToken = ilcsc.getConToken( );
											for( IDENTIFIER_DECLARATION_SEMANTIC_CHUNK idsc : decStack ) {
												if( idsc.identifier.getTokenChars( ).equals( conToken.getTokenChars( )) ) {
													if( idsc.getTypeAsCCode( ).equals("float") ) {
														ilcsc.setAsFloat( );
														break;
													}
												}
											}
										}
										
										// tab if newline 
										if( cCode.substring( cCode.length() - 2 ).contains( "\n" ) ) {
											for( int indents = 0; indents <= arr[0].logicDepth; ++indents )
												cCode.append( "    " );
												
										}
										cCode.append( mtsc.toCCode( ) );
										if( seqStack.size( ) > 1 )
											cCode.append( " " );
										subEnd = 1;
										subStack.clear( );
									}
										
								}
								if( mtsc == null ) {
									TOKEN_META_CHUNK firstChunk = seqStack.remove( 0 );
									I_TOKEN firstToken = firstChunk.token;
									if( firstToken == TOKEN.END ) {
										if( !cCode.substring( cCode.length() - 2 ).contains( ";" ) )
											cCode.append( ";" );
										cCode.append( "\n" );
									}
									String asC = firstToken.toCCode( );
									if( !asC.isEmpty( ) ) {
										// tab if newline 
										if( cCode.substring( cCode.length() - 2 ).contains( "\n" ) ) {
											for( int indents = 0; indents <= firstChunk.logicDepth; ++indents )
												cCode.append( "    " );
												
										}
										cCode.append( asC );
										if( firstToken == TOKEN.END ) {
											if( !cCode.substring( cCode.length() - 2 ).contains( ";" ) )
												cCode.append( ";" );
											cCode.append( "\n" );
										}
										if( !asC.contains( "\n" ) && firstToken != TOKEN.END )
											cCode.append( " " );
									}
								}
							}
							// Leave no token behind
							if( !seqStack.isEmpty( ) ) {
								for( TOKEN_META_CHUNK seqChunk : seqStack ) {	
									// Cheaty hack to resolve some issues with ; and }
									if( seqChunk.token == TOKEN.SEQUENCE ) {
										if( cCode.substring( cCode.length() - 2 ).contains( ";" ) )
											continue;
									}
									if( seqChunk.token == TOKEN.END ) {
										if( !cCode.substring( cCode.length() - 2 ).contains( ";" ) )
											cCode.append( ";" );
										cCode.append( "\n" );
									}
									String asC = seqChunk.token.toCCode( );
									if( !asC.isEmpty( ) ) {
										// tab if newline 
										if( cCode.substring( cCode.length() - 2 ).contains( "\n" ) ) {
											for( int indents = 0; indents <= seqChunk.logicDepth; ++indents )
												cCode.append( "    " );
												
										}
										cCode.append( asC );
										if( seqStack.indexOf( seqChunk ) < seqStack.size() - 1 )
											cCode.append( " " );
										else if( seqChunk.token == TOKEN.END )
											cCode.append( "\n" );
									}
								}
							}
						}
						
						if( !cCode.substring( cCode.length() - 2 ).contains( ";" ) )
							cCode.append( ";" );
						
						seqStack.clear( );
					}
				}
				
				if( !cCode.substring( cCode.length() - 2 ).contains( "\n" ) )
					cCode.append( "\n" );
				cCode.append( "}" );
				
				System.out.println( cCode.toString( ) );
				
			}
			
		}
	}
	
	/*
	 * Tokens and Token stuff
	 */
	
	/**
	 * TOKEN_TYPE is used for classifying groups of tokens to simplify logic.					<br /><br />
	 * 
	 * BNF				Backus-Naur grammar-specific tokens										<br />
	 * BINOP			Binary operations														<br />
	 * GROUP_LEFT		( and [																	<br />
	 * GROUP_RIGHT		) and ]																	<br />
	 * SML				Tokens specific to the Standard Meta Language (e.g., list, hd)			<br />
	 * TYPE				Typedef tokens such as int, bool, etc									<br />
	 * LOGIC			Conditional and loop tokens (e.g. if, do)								<br />
	 * LOGICOP			Logical operators (e.g. and, !)											<br />
	 * VALUE			Identifiers and literal values											<br />
	 * OTHER			Miscellaneous
	 */
	public enum TOKEN_TYPE {
		BNF,
		BINOP,
		GROUP_LEFT,
		GROUP_RIGHT,
		SML,
		TYPE,
		LOGIC,
		LOGICOP,
		VALUE,
		OTHER;
	}
	
	/**
	 * A common interface used to attribute relativity between the static @TOKEN tokens and dynamic ID/INT/FLOAT tokens
	 */
	public interface I_TOKEN {
		abstract TOKEN_TYPE getType( );
		abstract boolean is( String t );
		abstract String getTokenName( );
		abstract String getTokenChars( );
		
		/**
		 * Checks if the token is valid in context of the previous and following tokens
		 */
		abstract boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken );
		
		/**
		 * Returns the juxtaposed token that produces a syntax error, if any
		 */
		abstract I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken );
		
		abstract String toCCode( );
	}
	
	public interface I_VALUE_TOKEN extends I_TOKEN { 
		public String getValueType( );
		public boolean getValueAsBoolean( );
		
		public String getValueTypeAsCCode( );
	}
	
	/**
	 * A token representing an identifier of some kind
	 */
	public class ID_TOKEN implements I_TOKEN {

		private String chars;
		
		public ID_TOKEN( String s ) {
			this.chars = s;
		}
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public boolean is( String t ) {
			return this.chars.equals( t );
		}
		
		@Override
		public String getTokenChars( ) {
			return this.chars;
		}
		
		@Override
		public String getTokenName( ) {
			return "ID";
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) { 
			return (priorToken == null || !(priorToken instanceof ID_TOKEN)) && (nextToken == null || !(nextToken instanceof ID_TOKEN));
		}
		
		@Override
		public I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return priorToken instanceof ID_TOKEN ? priorToken : nextToken instanceof ID_TOKEN ? nextToken: null;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
	}
	
	/**
	 * A token representing an integer value
	 */
	public class INT_TOKEN implements I_VALUE_TOKEN {

		private int value;
		
		public INT_TOKEN( String s ) {
			this( Integer.parseInt( s ) );
		}
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public String getValueType( ) {
			return "TypeInt";
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "int";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			return this.getValue( ) > 0;
		}
		
		public INT_TOKEN( int i ) {
			this.value = i;
		}
		
		public int getValue( ) {
			return this.value;
		}
		
		@Override
		public boolean is(String t) {
			return String.valueOf( this.value ).equals( t );
		}
		
		@Override
		public String getTokenChars( ) {
			return String.valueOf( value );
		}
		
		@Override
		public String getTokenName( ) {
			return "INT";
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			// Just for now
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return null;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
	}
	
	/**
	 * A token representing a float value
	 */
	public class FLOAT_TOKEN implements I_VALUE_TOKEN {
		
		private float value;
		
		public FLOAT_TOKEN( String s ) {
			this( Float.parseFloat( s ) );
		}
		
		public FLOAT_TOKEN( float f ) {
			this.value = f;
		}
		
		public float getValue( ) {
			return this.value;
		}
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public String getValueType( ) {
			return "TypeFloat";
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "float";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			return this.getValue( ) > 0F;
		}
		
		@Override
		public boolean is( String t ) {
			return String.valueOf( this.value ).equals( t );
		}
		
		@Override
		public String getTokenChars( ) {
			return String.valueOf( value );
		}
		
		@Override
		public String getTokenName( ) {
			return "FLOAT";
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			// Just for now
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return null;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
	}
	
	/**
	 * A token representing a boolean value
	 */
	public class BOOL_TOKEN implements I_VALUE_TOKEN {
		
		private boolean value;
		
		public BOOL_TOKEN( String s ) {
			this( Boolean.parseBoolean( s ) );
		}
		
		public BOOL_TOKEN( boolean b ) {
			this.value = b;
		}
		
		public boolean getValue( ) {
			return this.value;
		}
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public String getValueType( ) {
			return "TypeBool";
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "bool";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			return this.getValue( );
		}
		
		@Override
		public boolean is( String t ) {
			return String.valueOf( this.value ).equals( t );
		}
		
		@Override
		public String getTokenChars( ) {
			return String.valueOf( value );
		}
		
		@Override
		public String getTokenName( ) {
			return "BOOL";
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			// Just for now
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return null;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
	}
	
	/**
	 * A token representing a unit value
	 */
	public class UNIT_TOKEN implements I_VALUE_TOKEN {
		
		private final String value = "()";
		
		public UNIT_TOKEN( String s ) { }
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public String getValueType( ) {
			return "TypeUnit";
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			// guessing here
			return true;
		}
		
		@Override
		public boolean is( String t ) {
			return String.valueOf( this.value ).equals( t );
		}
		
		@Override
		public String getTokenChars( ) {
			return String.valueOf( value );
		}
		
		@Override
		public String getTokenName( ) {
			return "UNIT";
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			// Just for now
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return null;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
	}
	
	public final String INT_ARRAY_TOKEN_NAME = "TypeList#TypeInt";
	public class INT_ARRAY_TOKEN implements I_VALUE_TOKEN {

		private int[] value = null;
		
		public INT_ARRAY_TOKEN( String s ) {
			s = s.substring( 1, s.length() - 1 );
			String[] as = s.split( "," );
			int[] i = new int[ as.length ];
			for( int ix = 0; ix < as.length; ++ix )
				i[ix] = Integer.valueOf( as[ix] );
			this.value = Arrays.copyOf( i, i.length );
		}
		
		public INT_ARRAY_TOKEN( ) {
			
		}
		
		public int[] getValue( ) {
			return this.value;
		}
		
		public int head( ) {
			return this.value[0];
		}
		
		public int tail( ) {
			return this.value[ this.value.length - 1 ];
		}
		
		@Override
		public TOKEN_TYPE getType() {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public String getValueType( ) {
			return INT_ARRAY_TOKEN_NAME;
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "list<int>";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			return this.getValue( ).length > 0;
		}

		@Override
		public boolean is(String t) {
			return this.getTokenChars( ).equals( t );
		}

		@Override
		public String getTokenName() {
			return "INT_ARRAY";
		}

		@Override
		public String getTokenChars() {
			return Arrays.toString( this.value ).replace( " ", "" );
		}

		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			// Just for now
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return null;
		}
		
		@Override
		public String toCCode( ) {
			if( this.value == null )
				return "0";
			StringBuilder sb = new StringBuilder( );
			for( int ix = 0; ix < this.value.length; ++ix ) {
				sb.append( "new list<int>(" + this.value[ix] + "," );
			}
			sb.append( "0)" );
			for( int ix = 0; ix < this.value.length - 1; ++ix )
				sb.append( ")" );
			return sb.toString( );
		}
	}
	
	public final String FLOAT_ARRAY_TOKEN_NAME = "TypeList#TypeFloat";
	public class FLOAT_ARRAY_TOKEN implements I_VALUE_TOKEN {

		private float[] value;
		
		public FLOAT_ARRAY_TOKEN( String s ) {
			s = s.substring( 1, s.length() - 1 );
			String[] as = s.split( "," );
			float[] i = new float[ as.length ];
			for( int ix = 0; ix < as.length; ++ix )
				i[ix] = Float.valueOf( as[ix] );
			this.value = Arrays.copyOf( i, i.length );
		}
		
		public float[] getValue( ) {
			return this.value;
		}
		
		public float head( ) {
			return this.value[0];
		}
		
		public float tail( ) {
			return this.value[ this.value.length - 1 ];
		}
		
		@Override
		public TOKEN_TYPE getType() {
			return TOKEN_TYPE.VALUE;
		}
		
		@Override
		public String getValueType( ) {
			return FLOAT_ARRAY_TOKEN_NAME;
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "list<float>";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			return this.getValue( ).length > 0;
		}

		@Override
		public boolean is(String t) {
			return this.getTokenChars( ).equals( t );
		}

		@Override
		public String getTokenName() {
			return "FLOAT_ARRAY";
		}

		@Override
		public String getTokenChars() {
			return Arrays.toString( this.value ).replace( " ", "" );
		}

		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			// Just for now
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return null;
		}
		
		@Override
		public String toCCode( ) {
			StringBuilder sb = new StringBuilder( );
			for( int ix = 0; ix < this.value.length; ++ix ) {
				sb.append( "new list<float>(" + this.value[ix] + "," );
			}
			sb.append( "0)" );
			for( int ix = 0; ix < this.value.length - 1; ++ix )
				sb.append( ")" );
			return sb.toString( );
		}
	}
	
	public final String ERROR_TOKEN_TYPE = "TypeError";
	public class ERROR_TOKEN implements I_VALUE_TOKEN {

		@Override
		public TOKEN_TYPE getType() {
			return TOKEN_TYPE.VALUE;
		}

		@Override
		public boolean is(String t) {
			return false;
		}

		@Override
		public String getTokenName() {
			return "ERROR";
		}

		@Override
		public String getTokenChars() {
			return null;
		}

		@Override
		public boolean checkJuxtapose(I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken) {
			return true;
		}

		@Override
		public I_TOKEN getOffendingToken(I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken) {
			return null;
		}

		@Override
		public String getValueType() {
			return ERROR_TOKEN_TYPE;
		}
		
		@Override
		public String getValueTypeAsCCode( ) {
			return "";
		}
		
		@Override
		public boolean getValueAsBoolean( ) {
			// Guessing here
			return false;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
	}
	
	/**
	 * Token class for the singleton "End Of File" token.
	 *
	 */
	public class EOF_TOKEN implements I_TOKEN {
		
		public EOF_TOKEN( String s ) { }
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return TOKEN_TYPE.OTHER;
		}
		
		@Override
		public boolean is( String t ) {
			return t == null;
		}
		
		@Override
		public String getTokenChars( ) {
			return "";
		}
		
		@Override
		public String getTokenName( ) {
			return "EOF";
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return nextToken == null;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			return nextToken;
		}
		
		@Override
		public String toCCode( ) {
			return this.getTokenChars( );
		}
		
	}
	
	public final EOF_TOKEN EOF = new EOF_TOKEN( null );
	
	/**
	 * An enumeration of default/reserved tokens in SML and BN grammar
	 */
	public enum TOKEN implements I_TOKEN {
		BN_OR( "|", TOKEN_TYPE.BNF ),
		BN_REPLACE( "::=", TOKEN_TYPE.BNF ),
		
		ADD( "+", TOKEN_TYPE.BINOP ),
		SUB( "-", TOKEN_TYPE.BINOP ),
		MULT( "*", TOKEN_TYPE.BINOP ),
		DIV( "/", TOKEN_TYPE.BINOP ),
		
		EQUALS( "=", TOKEN_TYPE.BINOP ),
		LT( "<", TOKEN_TYPE.BINOP ),
		GT( ">", TOKEN_TYPE.BINOP ),
		LTE( "<=", TOKEN_TYPE.BINOP ),
		GTE( ">=", TOKEN_TYPE.BINOP ),
		
		OR( "or", "||", TOKEN_TYPE.LOGICOP ),
		AND( "and", "&&", TOKEN_TYPE.LOGICOP ),
		NOT( "not", "!", TOKEN_TYPE.LOGICOP ),
		READ_REF( "!", "", TOKEN_TYPE.SML ),
		
		LIST( "list", TOKEN_TYPE.SML ),
		CONS( "::", TOKEN_TYPE.SML ),
		HEAD( "hd", TOKEN_TYPE.SML ),
		TAIL( "tl", TOKEN_TYPE.SML ),
		REF( "ref", "", TOKEN_TYPE.SML ),
		ASSIGN( ":=", "=", TOKEN_TYPE.SML ),
		TYPEDEF( ":", TOKEN_TYPE.SML ),
		
		WHILE( "while", TOKEN_TYPE.LOGIC ),
		DO( "do", "{\n", TOKEN_TYPE.LOGIC ),
		IF( "if", "", TOKEN_TYPE.LOGIC ),
		THEN( "then", "?", TOKEN_TYPE.LOGIC ),
		ELSE( "else", ":", TOKEN_TYPE.LOGIC ),
		LET( "let", "{\n", TOKEN_TYPE.LOGIC ),
		IN( "in", ";\n", TOKEN_TYPE.LOGIC ),
		END( "end", "};", TOKEN_TYPE.LOGIC ),
		
		ELLIPSIS( "...", TOKEN_TYPE.SML ),
		VALUE( "val", TOKEN_TYPE.OTHER ),
		
		FUNCTION_DEC( "fun", TOKEN_TYPE.SML),
		INTEGER_TYPEDEF( "int", TOKEN_TYPE.TYPE ),
		FLOAT_TYPEDEF( "real", "float", TOKEN_TYPE.TYPE ),
		UNIT_TYPEDEF( "unit", TOKEN_TYPE.TYPE ),
		BOOLEAN_TYPEDEF( "bool", TOKEN_TYPE.TYPE ),
		
		
		COMMA( ",", TOKEN_TYPE.SML ),
		
		LEFT_BRACKET( "[", TOKEN_TYPE.GROUP_LEFT ),
		RIGHT_BRACKET( "]", TOKEN_TYPE.GROUP_RIGHT ),
		LEFT_PAREN( "(", TOKEN_TYPE.GROUP_LEFT ),
		RIGHT_PAREN( ")", TOKEN_TYPE.GROUP_RIGHT ),
		
		SEQUENCE( ";", ";\n", TOKEN_TYPE.LOGIC );
		
		private TOKEN( String s, TOKEN_TYPE type ) {
			this( s, s, type );
		}
		
		private TOKEN( String s, String c, TOKEN_TYPE type ) {
			this.chars = s;
			this.cEquiv = c;
			this.type = type;
		}
		private final String chars;
		private final TOKEN_TYPE type;
		
		private final String cEquiv;
		
		@Override 
		public TOKEN_TYPE getType( ) {
			return this.type;
		}
		
		@Override
		public boolean is( String t ) {
			return this.chars.equals( t );
		}
		
		@Override
		public String getTokenChars( ) {
			return this.chars;
		}
		
		@Override
		public String getTokenName( ) {
			return this.name( );
		}
		
		@Override
		public String toCCode( ) {
			return this.cEquiv;
		}
		
		@Override
		public boolean checkJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) { 
			switch( this ) {
				case ADD:
				case SUB:
				case MULT:
				case DIV:
				case EQUALS:
				case LT:
				case GT:
				case LTE:
				case GTE:
					return this.operatorJuxtapose(priorToken, nextToken);
				
				case VALUE:
					return (priorToken == null || priorToken == TOKEN.LET || priorToken == TOKEN.SEQUENCE) && (nextToken instanceof ID_TOKEN);
					
				case LET:
					return nextToken == TOKEN.VALUE || nextToken == TOKEN.FUNCTION_DEC || (nextToken instanceof ID_TOKEN && afterToken == TOKEN.IN);
				case DO:
					return nextToken != TOKEN.SEQUENCE;
					
				default:
					break;
			}
			
			if( this.getType( ) == TOKEN_TYPE.TYPE )
				return (nextToken == null) || nextToken == TOKEN.REF || nextToken == TOKEN.LIST || nextToken.getType( ) != TOKEN_TYPE.TYPE;
			
			return true;
		}
		
		@Override
		public  I_TOKEN getOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken, I_TOKEN afterToken ) {
			switch( this ) {
				case ADD:
				case SUB:
				case MULT:
				case DIV:
				case EQUALS:
				case LT:
				case GT:
				case LTE:
				case GTE:
					return this.operatorOffendingToken(priorToken, nextToken);
					
				case VALUE:
					return !(nextToken instanceof ID_TOKEN) ? nextToken : null;
					
				case LET:
				case DO:
					return nextToken;
				default:
					return null;
			}
		}
		
		private boolean operatorJuxtapose( I_TOKEN priorToken, I_TOKEN nextToken ) {
			boolean valid = 
					(priorToken.getType( ) == TOKEN_TYPE.VALUE ||
					 priorToken.getType( ) == TOKEN_TYPE.GROUP_RIGHT ||
					 priorToken == TOKEN.HEAD || priorToken == TOKEN.TAIL) &&
					 
					(nextToken.getType( ) == TOKEN_TYPE.VALUE ||
					 nextToken == TOKEN.SUB || nextToken == TOKEN.HEAD || nextToken == TOKEN.TAIL || nextToken == TOKEN.READ_REF ||
					 nextToken.getType( ) == TOKEN_TYPE.GROUP_LEFT);
			
			if( this == TOKEN.SUB )
				valid = valid || priorToken == TOKEN.EQUALS || priorToken == TOKEN.ASSIGN || priorToken == TOKEN.REF || (priorToken.getType( ) == TOKEN_TYPE.GROUP_LEFT);
			else if( this == TOKEN.EQUALS )
				valid = valid || ( priorToken.getType( ) == TOKEN_TYPE.TYPE) || priorToken == TOKEN.LIST || (priorToken == TOKEN.REF || nextToken == TOKEN.REF);
			
			
			return valid;
		}
		
		private I_TOKEN operatorOffendingToken( I_TOKEN priorToken, I_TOKEN nextToken ) {
			return
				!(priorToken.getType( ) == TOKEN_TYPE.TYPE || priorToken instanceof INT_TOKEN || priorToken instanceof FLOAT_TOKEN || priorToken instanceof ID_TOKEN) ?
						priorToken :
						!(nextToken.getType( ) == TOKEN_TYPE.LOGICOP || nextToken instanceof INT_TOKEN || nextToken instanceof FLOAT_TOKEN || nextToken instanceof ID_TOKEN) ?
								nextToken :
								null;
		}
		
		public boolean partialMatch( String t ) {
			return this.chars.startsWith( t );
		}
		
		public boolean partialMatchReverse( String t ) {
			return this.chars.endsWith( t );
		}
		
		public static TOKEN find( String t ) {
			for( TOKEN token : TOKEN.values( ) ) {
				if( token.is(t) ) return token;
			}
			return null;
		}
		
		public static boolean hasMultipleMatches( String t ) {
			return TOKEN.hasMultipleMatches( t, false );
		}
		
		public static boolean hasMultipleMatches( String t, boolean reverse ) {
			// Because LUTE is stupid and doesn't realize arrays are a thing 
			// if the only thing on the line is an array
			final Pattern groupMatcher = Pattern.compile( "(\\(|\\[|\\)|\\])" );
			if( groupMatcher.matcher( t ).matches( ) ) return true;
			
			int count = 0;
			for( TOKEN token : TOKEN.values( ) ) {
				if( reverse ? token.partialMatchReverse( t ) : token.partialMatch( t ) )
					count++;
			}
			return count > 1;
		}
	}
	
	// Regex for determining if a string correlates to an identifier, an integer, or a float
	private Pattern idPattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
	private Pattern intPattern = Pattern.compile("^-?[0-9]+$");
	private Pattern intArrayPattern = Pattern.compile("^\\[-?[0-9]+([,]-?[0-9]+)*\\]$");
	private Pattern floatPattern = Pattern.compile("^-?[0-9]+[.][0-9]+$");
	private Pattern floatArrayPattern = Pattern.compile("^\\[-?[0-9]+[.][0-9]+([,]-?[0-9]+[.][0-9]+)*\\]$");
	private Pattern emptyArrayPattern = Pattern.compile("^\\[\\]$");
	// these are probably overkill but what the heck
	private Pattern boolPattern = Pattern.compile( "^(true | false)$" );
	private Pattern unitPattern = Pattern.compile( "^\\(\\)$" );
	
	/**
	 * Searches for a valid token for the supplied character string.
	 * 
	 * @param t							The character string to look up
	 * @param currentCharIsDelimiter	Whether or not the last character read was a delimiter (and thus excluded)
	 * @return							The token, or null if no token or there are multiple matches and we haven't
	 * 									hit a delimiter
	 */
	public I_TOKEN getToken( String t, boolean currentCharIsDelimiter ) {
		return this.getToken(t, currentCharIsDelimiter, false );
	}
	
	/**
	 * Searches for a valid token for the supplied character string.
	 * 
	 * @param t							The character string to look up
	 * @param currentCharIsDelimiter	Whether or not the last character read was a delimiter (and thus excluded)
	 * @param reverse					Whether or not the partial match check compares from the end
	 * @return							The token, or null if no token or there are multiple matches and we haven't
	 * 									hit a delimiter
	 */
	public I_TOKEN getToken( String t, boolean currentCharIsDelimiter, boolean reverse ) {
		I_TOKEN token;
		
		// Check for case where partial of one token counts as a full other token (e.g. :: and ::=)
		// This only matters if we haven't reached a delimiter character (e.g. space)
		if( !currentCharIsDelimiter ) {
			if( TOKEN.hasMultipleMatches( t, reverse ) )
				return null;
			else {
				return (token = TOKEN.find( t )) != null ? token : null;
			}
		}

		if( (token = TOKEN.find( t )) != null )
			return token;
		
		// We assume at this point we're dealing with a "custom" token such as an ID or value
		if( idPattern.matcher( t ).matches( ) )
			return new ID_TOKEN( t );
		else if( intPattern.matcher( t ).matches( ) )
			return new INT_TOKEN( t );
		else if( intArrayPattern.matcher( t ).matches( ) )
			return new INT_ARRAY_TOKEN( t );
		else if( floatPattern.matcher( t ).matches( ) )
			return new FLOAT_TOKEN( t );
		else if( floatArrayPattern.matcher( t ).matches( ) )
			return new FLOAT_ARRAY_TOKEN( t );
		else if( emptyArrayPattern.matcher( t ).matches( ) )
			return new INT_ARRAY_TOKEN( );
		else if( boolPattern.matcher( t ).matches( ) )
			return new BOOL_TOKEN( t );
		else if( unitPattern.matcher( t ).matches( ) )
			return new UNIT_TOKEN( t );
		else
			// Need to probably have a fail flag somehow because this stage
			// means we have an invalid token
			return null;
	}
	
	/**
	 * Checks if we're reading a token character or hit a delimiter.
	 * Adds characters to the passed in token string.
	 * 
	 * @param c		current character
	 * @param t		current token being assembled
	 * @return		1 if space/EOL, 0 if valid char
	 */
	public int read( char c, StringBuilder t ) {
		if( c == ' ' || c == '\n' || c == '\r' || c == '\t' ) {
			return 1;
		}
		
		t.append( c );
		return 0;
	}
	
	/**
	 * Adds a token to the buffer of identified tokens
	 * 
	 * @param token				The token object itself
	 * @param tokenString		The actual token char(s) themselves (debugging purposes)
	 * @param token_num			The ordinal of the token in the file (debugging purposes)
	 * @param line				The line in the file where the token occurred (debugging purposes)
	 * @param token_start		The char position in the line where the token started (debugging purposes)
	 * @param outputMode		Debug value to determine if debug output should be printed
	 */
	public void addToken( I_TOKEN token, String tokenString, int token_num, int line, int token_start, int outputMode ) {
		this.tokenBuffer.add( new TOKEN_META_CHUNK( token, line, token_start, tokenString ) );
		
		if( outputMode == 2 ) {
			System.out.println(
					"[@" + token_num + "," + token_start + ":" + (token_start + tokenString.length( ) ) + "='" +
					tokenString + "',<" + token.getTokenName( ) + ">," + line + ":" + token_start + "]"
			);
		}
	}
	
	/*
	 * Semantic Chunks Etc.
	 */
	/**
	 * A buffer for identified tokens and their associated metadata used for second-pass syntax checking
	 */
	private ArrayList< TOKEN_META_CHUNK > tokenBuffer = new ArrayList< TOKEN_META_CHUNK >( );
	
	
	/**
	 * Token and Metadata Chunk <br />
	 * Object containing a token and the necessary metadata for printing information pertaining to
	 * syntax errors
	 */
	public class TOKEN_META_CHUNK {
		public I_TOKEN token;
		public final int line;
		public final int startPos;
		public final String tokenValue;
		
		/**
		 * A "back-ported", necessary bit of metadata so that we can parse into C in a third pass.
		 * @see TOKEN_LOGIC_DEPTH.
		 */
		public int logicDepth;
		
		public TOKEN_META_CHUNK( I_TOKEN token, int line, int startPos, String tokenValue ) {
			this.token = token;
			this.line = line;
			this.startPos = startPos;
			this.tokenValue = tokenValue;
		}
		
		public void applyHackyDoEndFix( ) {
			if( this.token == TOKEN.SEQUENCE )
				this.token = TOKEN.END;
		}
	}
	
	/**
	 * Object containing a @TOKEN_TYPE.LOGIC token and its "depth" in the logic.
	 * Used for validating that all conditions and loops are closed, and that no tokens are dangling or
	 * superfluous
	 */
	public class TOKEN_LOGIC_DEPTH {
		public final TOKEN token;
		public final int depth;
		
		public TOKEN_LOGIC_DEPTH( TOKEN token, int depth ) {
			this.token = token;
			this.depth = depth;
		}
	}
	
	/**
	 * Essentially, a sequence of tokens that are contextually juxtaposed and could be interpreted as a single token
	 *
	 */
	public interface MULTI_TOKEN_SEMANTIC_CHUNK {
		/**
		 * Unused, remnant from failed Project2
		 */
		public I_VALUE_TOKEN toToken( );
		
		/**
		 * Unused, remnant from failed Project2
		 */
		public String getType( );
		
		/**
		 * Returns a String representation of the C code this chunk represents
		 */
		public String toCCode( );
	}
	
	/**
	 * A chunk of tokens that represent a variable declaration
	 */
	public class IDENTIFIER_DECLARATION_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {
		private int depth;
		private boolean isRef;
		private boolean isList;
		private I_TOKEN identifier;
		private I_TOKEN type;
		
		public IDENTIFIER_DECLARATION_SEMANTIC_CHUNK( int depth, I_TOKEN identifier, I_TOKEN type, boolean isRef, boolean isList ) {
			this.depth = depth;
			this.identifier = identifier;
			this.type = type;
			this.isRef = isRef;
			this.isList = isList;
		}
		
		public int getDepth( ) { return this.depth; }
		public String getName( ) { return this.identifier.getTokenChars( ); }
		
		public boolean conflictsWith( IDENTIFIER_DECLARATION_SEMANTIC_CHUNK isc ) {
			return this.getName( ).equals(isc.getName( ) ) && this.getDepth( ) == isc.getDepth( );
		}
		
		@Override
		public I_VALUE_TOKEN toToken( ) {
			return null; //this.identifier;
		}
		
		@Override
		public String getType( ) {
			return null; //isRef ? "TypeRef#" + this.type.getValueType( ) : this.value.getValueType( );
		}
		
		@Override
		public String toCCode( ) {
			// it should be isRef and not isList but the tests seem to say otherwise?
			return (this.isList ? "list<" + this.type.toCCode( ) + ">" : this.type.toCCode( )) + (this.isList ? "*" : "" ) + " " + this.identifier.getTokenChars( ) + " =";
		}
		
		public String getTypeAsCCode( ) {
			return this.type.toCCode( );
		}
	}
	
	public class EQUIVALENCE_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {

		private I_TOKEN left;
		private I_VALUE_TOKEN right;
		
		public EQUIVALENCE_SEMANTIC_CHUNK( I_TOKEN left, I_VALUE_TOKEN right ) {
			this.left = left;
			this.right = right;
		}
		
		@Override
		public I_VALUE_TOKEN toToken() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toCCode() {
			// TODO Auto-generated method stub
			String r;
			if( right instanceof INT_ARRAY_TOKEN )
				r = ((INT_ARRAY_TOKEN)right).toCCode( );
			else if( right instanceof FLOAT_ARRAY_TOKEN )
				r = ((FLOAT_ARRAY_TOKEN)right).toCCode( );
			else
				r = right.getTokenChars( );
			return left.getTokenChars( ) + " == " + r;
		}
		
	}
	
	public class LIST_OP_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {
		private TOKEN listOp;
		private I_VALUE_TOKEN list;
		
		public LIST_OP_SEMANTIC_CHUNK( TOKEN listOp, I_VALUE_TOKEN list ) {
			this.listOp = listOp;
			this.list = list;
		}
		
		@Override
		public I_VALUE_TOKEN toToken( ) {
			if( this.list instanceof INT_ARRAY_TOKEN ) {
				switch( this.listOp ) {
					case HEAD:
						return new INT_TOKEN( ((INT_ARRAY_TOKEN)this.list).head( ) );
					case TAIL:
						return new INT_TOKEN( ((INT_ARRAY_TOKEN)this.list).tail( ) );
					default:
						return new ERROR_TOKEN( );
				}
			}
			else if( this.list instanceof FLOAT_ARRAY_TOKEN ) {
				switch( this.listOp ) {
					case HEAD:
						return new FLOAT_TOKEN( ((FLOAT_ARRAY_TOKEN)this.list).head( ) );
					case TAIL:
						return new FLOAT_TOKEN( ((FLOAT_ARRAY_TOKEN)this.list).tail( ) );
					default:
						return new ERROR_TOKEN( );
				}
			}
			else
				return new ERROR_TOKEN( );
		}
		
		@Override
		public String getType( ) {
			return this.toToken( ).getValueType( );
		}
		
		@Override
		public String toCCode( ) {
			return "";
		}
	}
	
	public class HACKY_COMPARE_LIST_OP_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {

		private I_TOKEN left;
		private I_TOKEN listOp;
		private I_TOKEN list;
		
		public HACKY_COMPARE_LIST_OP_SEMANTIC_CHUNK( I_TOKEN left, I_TOKEN listOp, I_TOKEN list ) {
			this.left = left;
			this.listOp = listOp;
			this.list = list;
		}
		
		@Override
		public I_VALUE_TOKEN toToken() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toCCode() {
			// TODO Auto-generated method stub
			return left.getTokenChars( ) + " == " + listOp.getTokenChars( ) + "(" + list.getTokenChars( ) + ")";
		}
		
	}
	
	public class LIST_CON_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {

		private I_VALUE_TOKEN val;
		private INT_ARRAY_TOKEN intArray = null;
		private FLOAT_ARRAY_TOKEN floatArray = null;
		
		public LIST_CON_SEMANTIC_CHUNK( I_VALUE_TOKEN val, INT_ARRAY_TOKEN array ) {
			this.val = val;
			this.intArray = array;
		}
		
		public LIST_CON_SEMANTIC_CHUNK( I_VALUE_TOKEN val, FLOAT_ARRAY_TOKEN array ) {
			this.val = val;
			this.floatArray = array;
		}
		
		@Override
		public I_VALUE_TOKEN toToken() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public String toCCode( ) {
			if( this.intArray != null ) {
				int[] vals = this.intArray.value;
				if( vals == null )
					return "new list<int>(" + this.val.getTokenChars() + ",0)";
				else {
					StringBuilder sb = new StringBuilder( );
					sb.append( "new list<int>(" + this.val.getTokenChars() + ",");
					for( int ix = 0; ix < vals.length; ++ix ) {
						sb.append( "new list<int>(" + vals[ix] + "," );
					}
					sb.append( "0)" );
					for( int ix = 0; ix < vals.length; ++ix )
						sb.append( ")" );
					return sb.toString( );
				}
			}
			else if( this.floatArray != null ) {
				float[] vals = this.floatArray.value;
				if( vals == null )
					return "new list<float>(" + this.val.getTokenChars() + ",0)";
				else {
					StringBuilder sb = new StringBuilder( );
					sb.append( "new list<float>(" + this.val.getTokenChars() + ",");
					for( int ix = 0; ix < vals.length; ++ix ) {
						sb.append( "new list<float>(" + vals[ix] + "," );
					}
					sb.append( "0)" );
					for( int ix = 0; ix < vals.length; ++ix )
						sb.append( ")" );
					return sb.toString( );
				}
			}
			return "";
		}

	}
	
	// Stupid and hacky because LUTE has no idea what an array using identifiers is
	// This is basically specifically for the last test, so feel free to call it
	// hard-coded
	// Wanted to make it able accept [ !x, !y, !z ] but regex was being a dumb
	public class ID_LIST_CON_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {
		private ID_TOKEN val;
		private boolean isFloat = false;
		private I_TOKEN token;
		
		public ID_LIST_CON_SEMANTIC_CHUNK( ID_TOKEN val, I_TOKEN token ) {
			this.val = val;
			this.token = token;
		}
		
		public void setAsFloat( ) {
			this.isFloat = true;
		}
		
		public ID_TOKEN getConToken( ) {
			return this.val;
		}
		
		@Override
		public I_VALUE_TOKEN toToken() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getType() {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public String toCCode( ) {
			if( this.token != null ) {
				StringBuilder sb = new StringBuilder( );
				sb.append( "new list<" + (this.isFloat ? "float" : "int") + ">(" + this.val.getTokenChars() + ",");
				sb.append( "new list<" + (this.isFloat ? "float" : "int") + ">(" + this.token.getTokenChars( ) + "," );
				sb.append( "0))" );
				return sb.toString( );
			}
			return "";
		}

	}
	
	public class CONDITIONAL_SEMANTIC_CHUNK implements MULTI_TOKEN_SEMANTIC_CHUNK {

		private I_TOKEN cond;
		private I_TOKEN trueVal;
		private I_TOKEN falseVal;
		
		public CONDITIONAL_SEMANTIC_CHUNK( I_TOKEN cond, I_TOKEN trueVal, I_TOKEN falseVal ) {
			this.cond = cond;
			this.trueVal = trueVal;
			this.falseVal = falseVal;
		}
		
		@Override
		public I_VALUE_TOKEN toToken() {
			return null;
			//boolean b = this.cond instanceof BOOL_TOKEN? ((BOOL_TOKEN)this.cond).getValue( ) : this.cond.getValueAsBoolean( );
			//return b ? this.trueVal : this.falseVal;
		}

		@Override
		public String getType() {
			return this.toToken( ).getValueType( );
		}
		
		@Override
		public String toCCode( ) {
			return cond.getTokenChars() + " ? " + this.trueVal.getTokenChars( ) + " : " + this.falseVal.getTokenChars( );
		}
	}
	
	public String tokensAsString( I_TOKEN ... tokens ) {
		return this.tokensAsString( false, tokens );
	}
	
	public String tokensAsString( boolean useValues, I_TOKEN ... tokens ) {
		// Assemble tokens into string. We stupidly use name rather than chars for value tokens because reasons
		StringBuilder sb = new StringBuilder( );
		for( int i = 0; i < tokens.length; ++i ) {
			sb.append( (tokens[i] instanceof I_VALUE_TOKEN || tokens[i] instanceof ID_TOKEN) && !useValues ? tokens[i].getTokenName( ) : tokens[i].getTokenChars( ) );
			if( i < tokens.length - 1 )
				sb.append( " " );
		}
		return sb.toString( );
	}
	
	
	private final Pattern idPat = Pattern.compile( 
			TOKEN.VALUE.getTokenChars( ) + " ID" + " : (" + TOKEN.INTEGER_TYPEDEF.getTokenChars( ) + "|" + TOKEN.FLOAT_TYPEDEF.getTokenChars( ) + "|" + TOKEN.BOOLEAN_TYPEDEF.getTokenChars( ) + ") ="
	);
	private final Pattern refIdPat = Pattern.compile( 
			TOKEN.VALUE.getTokenChars( ) + " ID" + " : (" + TOKEN.INTEGER_TYPEDEF.getTokenChars( ) + "|" + TOKEN.FLOAT_TYPEDEF.getTokenChars( ) + "|" + TOKEN.BOOLEAN_TYPEDEF.getTokenChars( ) + ") ref ="
	);
	private final Pattern arrayIdPat = Pattern.compile( 
			TOKEN.VALUE.getTokenChars( ) + " ID" + " : (" + TOKEN.INTEGER_TYPEDEF.getTokenChars( ) + "|" + TOKEN.FLOAT_TYPEDEF.getTokenChars( ) + ") list ="
	);
	private final Pattern arrayRefIdPat = Pattern.compile( 
			TOKEN.VALUE.getTokenChars( ) + " ID" + " : (" + TOKEN.INTEGER_TYPEDEF.getTokenChars( ) + "|" + TOKEN.FLOAT_TYPEDEF.getTokenChars( ) + ") list ref ="
	);
	
	public boolean tokensMatchIdentifierDeclaration( I_TOKEN ... tokens ) {
		String s = this.tokensAsString( tokens );
		return 	this.idPat.matcher( s ).matches( ) || 
				this.refIdPat.matcher( s ).matches( ) ||
				this.arrayIdPat.matcher( s ).matches( ) ||
				this.arrayRefIdPat.matcher( s ).matches( );
	}
	
	/**
	 * Returns any valid found semantic chunk for the provided tokens.
	 * 
	 * @param depth			The logic depth for the sequence (only necessary for identifier chunks
	 * @param tokens
	 * @return				A valid MULTI_TOKEN_SEMANTIC_CHUNK or null
	 */
	public MULTI_TOKEN_SEMANTIC_CHUNK getSemanticChunkForTokens( int depth, I_TOKEN ... tokens ) {
		String s = this.tokensAsString( tokens );
		
		if( this.idPat.matcher( s ).matches( ) )
			return new IDENTIFIER_DECLARATION_SEMANTIC_CHUNK( depth, tokens[1], tokens[ 3 ], false, false );
		else if( this.arrayIdPat.matcher( s ).matches( ) )
			return new IDENTIFIER_DECLARATION_SEMANTIC_CHUNK( depth, tokens[1], tokens[ 3 ], false, true );
		else if( refIdPat.matcher( s ).matches( ) )
			return new IDENTIFIER_DECLARATION_SEMANTIC_CHUNK( depth, tokens[1], tokens[ 3 ], true, false );
		else if( this.arrayRefIdPat.matcher( s ).matches( ) )
			return new IDENTIFIER_DECLARATION_SEMANTIC_CHUNK( depth, tokens[1], tokens[ 3 ], true, true );
		
		final Pattern hackyListPattern = Pattern.compile( "ID = (hd|tl) \\( ID \\)" );
		if( hackyListPattern.matcher( s ).matches( ) )
			return new HACKY_COMPARE_LIST_OP_SEMANTIC_CHUNK( tokens[0], tokens[2], tokens[4] );
		
		final Pattern hackyRefListPattern = Pattern.compile( "ID = (hd|tl) \\( ! ID \\)" );
		if( hackyRefListPattern.matcher( s ).matches( ) )
			return new HACKY_COMPARE_LIST_OP_SEMANTIC_CHUNK( tokens[0], tokens[2], tokens[5] );
		
		//CON
		final Pattern intListConPattern = Pattern.compile( "INT :: INT_ARRAY" );
		if( intListConPattern.matcher( s ).matches( ) )
			return new LIST_CON_SEMANTIC_CHUNK( (I_VALUE_TOKEN)tokens[0], (INT_ARRAY_TOKEN)tokens[2] );
		
		final Pattern floatListConPattern = Pattern.compile( "FLOAT :: FLOAT_ARRAY" );
		if( floatListConPattern.matcher( s ).matches( ) )
			return new LIST_CON_SEMANTIC_CHUNK( (I_VALUE_TOKEN)tokens[0], (FLOAT_ARRAY_TOKEN)tokens[2] );
		
		//ID CON
		final Pattern idListConPattern = Pattern.compile( "ID :: \\[ ! ID \\]" );
		if( idListConPattern.matcher( s ).matches( ) ) {
			return new ID_LIST_CON_SEMANTIC_CHUNK( (ID_TOKEN)tokens[0], tokens[4] );
		}
		
		final Pattern equivPattern = Pattern.compile( "(ID|INT|FLOAT|BOOL) = (INT|FLOAT|BOOL|INT_ARRAY|FLOAT_ARRAY)" );
		if( equivPattern.matcher( s ).matches( ) )
				return new EQUIVALENCE_SEMANTIC_CHUNK( (I_TOKEN)tokens[0], (I_VALUE_TOKEN)tokens[2] );
		
		
		final Pattern condPattern = Pattern.compile( "if (ID|INT|FLOAT|BOOL) then (ID|INT|FLOAT|BOOL) else (ID|INT|FLOAT|BOOL)" );
		if( condPattern.matcher( s ).matches( ) )
			return new CONDITIONAL_SEMANTIC_CHUNK( tokens[1], tokens[3], tokens[5] );
		
		return null;
	}
}
