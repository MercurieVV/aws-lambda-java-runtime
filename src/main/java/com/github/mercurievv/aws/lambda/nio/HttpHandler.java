package com.github.mercurievv.aws.lambda.nio;

import java.nio.ByteBuffer;

/**
 * Created with IntelliJ IDEA.
 * User: Victor Mercurievv
 * Date: 4/20/2019
 * Time: 1:11 PM
 * Contacts: email: mercurievvss@gmail.com Skype: 'grobokopytoff' or 'mercurievv'
 */
public interface HttpHandler {
    void onHeader(HttpResponse response);

    void onBody(ByteBuffer byteBuffer);
}
