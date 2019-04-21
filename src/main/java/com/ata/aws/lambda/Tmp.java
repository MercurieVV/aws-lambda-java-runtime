package com.ata.aws.lambda;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created with IntelliJ IDEA.
 * User: Victor Mercurievv
 * Date: 4/22/2019
 * Time: 1:24 AM
 * Contacts: email: mercurievvss@gmail.com Skype: 'grobokopytoff' or 'mercurievv'
 */
public class Tmp {
    AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
    InetSocketAddress hostAddress = new InetSocketAddress("localhost", 4999);
    Future<Void> future = client.connect(hostAddress);

    public Tmp() throws IOException {
    }

    public String sendMessage(String message) throws ExecutionException, InterruptedException {
        byte[] byteMsg = new String(message).getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(byteMsg);
        Future<Integer> writeResult = client.write(buffer);

        // do some computation

        writeResult.get();
        buffer.flip();
        Future<Integer> readResult = client.read(buffer);

        // do some computation

        readResult.get();
        String echo = new String(buffer.array()).trim();
        buffer.clear();
        return echo;
    }
}
