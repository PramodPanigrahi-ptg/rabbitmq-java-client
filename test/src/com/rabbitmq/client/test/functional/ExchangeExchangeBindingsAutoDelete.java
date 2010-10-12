//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.BrokerTestCase;

public class ExchangeExchangeBindingsAutoDelete extends BrokerTestCase {

    private Channel secondaryChannel = null;

    @Override
    protected void createResources() throws IOException {
        super.createResources();
        maybeCreateSecondaryChannel();
    }

    private void maybeCreateSecondaryChannel() throws IOException {
        if (null != secondaryChannel && ! secondaryChannel.isOpen()) {
            secondaryChannel = null;
        }
        if (null == secondaryChannel) {
            secondaryChannel = connection.createChannel();
        }
    }

    @Override
    protected void releaseResources() throws IOException {
        super.releaseResources();
        if (null != secondaryChannel && secondaryChannel.isOpen()) {
            secondaryChannel.close();
        }
    }

    private void assertExchangeNotExists(String name) throws IOException {
        maybeCreateSecondaryChannel();
        try {
            secondaryChannel.exchangeDeclarePassive(name);
            fail("Exchange " + name + " still exists.");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
        }
    }

    /*
     * build (A -> B) and (B -> A) and then delete one binding and both
     * exchanges should autodelete
     */
    public void testAutoDeleteExchangesSimpleLoop() throws IOException {
        channel.exchangeDeclare("A", "fanout", false, true, null);
        channel.exchangeDeclare("B", "fanout", false, true, null);
        channel.exchangeBind("A", "B", "");
        channel.exchangeBind("B", "A", "");

        channel.exchangeUnbind("A", "B", "");
        assertExchangeNotExists("A");
        assertExchangeNotExists("B");
    }

    /*
     * build (A -> B) (B -> C) (C -> D) and then delete D. All should autodelete
     */
    public void testTransientAutoDelete() throws IOException {
        channel.exchangeDeclare("A", "fanout", false, true, null);
        channel.exchangeDeclare("B", "fanout", false, true, null);
        channel.exchangeDeclare("C", "fanout", false, true, null);
        channel.exchangeDeclare("D", "fanout", false, true, null);

        channel.exchangeBind("B", "A", "");
        channel.exchangeBind("C", "B", "");
        channel.exchangeBind("D", "C", "");

        channel.exchangeDelete("D");

        assertExchangeNotExists("A");
        assertExchangeNotExists("B");
        assertExchangeNotExists("C");
        assertExchangeNotExists("D");
    }

    /*
     * build (A -> B) (B -> C) (C -> D) (Source -> A) (Source -> B) (Source ->
     * C) (Source -> D) On removal of D, all should autodelete
     */
    public void testRepeatedTargetAutoDelete() throws IOException {
        channel.exchangeDeclare("A", "fanout", false, true, null);
        channel.exchangeDeclare("B", "fanout", false, true, null);
        channel.exchangeDeclare("C", "fanout", false, true, null);
        channel.exchangeDeclare("D", "fanout", false, true, null);
        channel.exchangeDeclare("Source", "fanout", false, true, null);

        channel.exchangeBind("B", "A", "");
        channel.exchangeBind("C", "B", "");
        channel.exchangeBind("D", "C", "");

        channel.exchangeBind("A", "Source", "");
        channel.exchangeBind("B", "Source", "");
        channel.exchangeBind("C", "Source", "");
        channel.exchangeBind("D", "Source", "");

        channel.exchangeDelete("A");
        // Source should still be there. We'll verify this by redeclaring
        // it here and verifying it goes away later
        channel.exchangeDeclare("Source", "fanout", false, true, null);

        channel.exchangeDelete("D");

        assertExchangeNotExists("Source");
        assertExchangeNotExists("A");
        assertExchangeNotExists("B");
        assertExchangeNotExists("C");
        assertExchangeNotExists("D");
    }
}