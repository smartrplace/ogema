/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.ogema.impl.persistence;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author jlapp
 */
public class ByteBufferDataInput implements DataInput {
	
	final ByteBuffer bb;

	public ByteBufferDataInput(ByteBuffer bb) {
		this.bb = bb;
	}

	@Override
	public void readFully(byte[] b) throws IOException {
		bb.get(b);
	}

	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		bb.get(b, off, len);
	}

	@Override
	public int skipBytes(int n) throws IOException {
		int skip = Math.min(n, bb.capacity() - bb.position());
		bb.position(bb.position() + skip);
		return skip;
	}

	@Override
	public boolean readBoolean() throws IOException {
		return bb.get() != 0;
	}

	@Override
	public byte readByte() throws IOException {
		return bb.get();
	}

	@Override
	public int readUnsignedByte() throws IOException {
		byte b = bb.get();
		return b < 0 ? 256 + b : b;
	}

	@Override
	public short readShort() throws IOException {
		return bb.getShort();
	}

	@Override
	public int readUnsignedShort() throws IOException {
		return ((bb.get() & 0xFF) << 8) | (bb.get() & 0xFF);
	}

	@Override
	public char readChar() throws IOException {
		return bb.getChar();
	}

	@Override
	public int readInt() throws IOException {
		return bb.getInt();
	}

	@Override
	public long readLong() throws IOException {
		return bb.getLong();
	}

	@Override
	public float readFloat() throws IOException {
		return bb.getFloat();
	}

	@Override
	public double readDouble() throws IOException {
		return bb.getDouble();
	}

	@Override
	public String readLine() throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public String readUTF() throws IOException {
		// System.out.printf(" --- readUTF @ %d%n", position());
		return DataInputStream.readUTF(this);
	}
	
	public int position() {
		return bb.position();
	}
	
	public int getFilePointer() {
		return bb.position();
	}
	
	public void seek(int position) {
		bb.position(position);
	}
	
	public void close() {
	}
	
}
