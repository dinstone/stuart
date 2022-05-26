/*
 * Copyright 2019 Yang Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.stuart.entities.metrics;

import java.io.Serializable;
import java.util.concurrent.atomic.LongAdder;

public class MqttMetrics implements Serializable {

    private static final long serialVersionUID = 7602482777448548414L;

    private static volatile MqttMetrics instance;

    //
    // private LongAdder packetReceived;

    //
    // private LongAdder packetSent;

    private LongAdder packetConnect;

    private LongAdder packetConnack;

    private LongAdder packetDisconnect;

    private LongAdder packetPingreq;

    private LongAdder packetPingresp;

    private LongAdder packetPublishReceived;

    private LongAdder packetPublishSent;

    private LongAdder packetPubackReceived;

    private LongAdder packetPubackSent;

    private LongAdder packetPubackMissed;

    private LongAdder packetPubcompReceived;

    private LongAdder packetPubcompSent;

    private LongAdder packetPubcompMissed;

    private LongAdder packetPubrecReceived;

    private LongAdder packetPubrecSent;

    private LongAdder packetPubrecMissed;

    private LongAdder packetPubrelReceived;

    private LongAdder packetPubrelSent;

    private LongAdder packetPubrelMissed;

    private LongAdder packetSubscribe;

    private LongAdder packetSuback;

    private LongAdder packetUnsubscribe;

    private LongAdder packetUnsuback;

    //
    // private LongAdder messageReceived;

    //
    // private LongAdder messageSent;

    private LongAdder messageDropped;

    private LongAdder messageQos0Received;

    private LongAdder messageQos0Sent;

    private LongAdder messageQos1Received;

    private LongAdder messageQos1Sent;

    private LongAdder messageQos2Received;

    private LongAdder messageQos2Sent;

    private LongAdder byteReceived;

    private LongAdder byteSent;

    private MqttMetrics() {
        // this.packetReceived = new LongAdder();
        // this.packetSent = new LongAdder();
        this.packetConnect = new LongAdder();
        this.packetConnack = new LongAdder();
        this.packetDisconnect = new LongAdder();
        this.packetPingreq = new LongAdder();
        this.packetPingresp = new LongAdder();
        this.packetPublishReceived = new LongAdder();
        this.packetPublishSent = new LongAdder();
        this.packetPubackReceived = new LongAdder();
        this.packetPubackSent = new LongAdder();
        this.packetPubackMissed = new LongAdder();
        this.packetPubcompReceived = new LongAdder();
        this.packetPubcompSent = new LongAdder();
        this.packetPubcompMissed = new LongAdder();
        this.packetPubrecReceived = new LongAdder();
        this.packetPubrecSent = new LongAdder();
        this.packetPubrecMissed = new LongAdder();
        this.packetPubrelReceived = new LongAdder();
        this.packetPubrelSent = new LongAdder();
        this.packetPubrelMissed = new LongAdder();
        this.packetSubscribe = new LongAdder();
        this.packetSuback = new LongAdder();
        this.packetUnsubscribe = new LongAdder();
        this.packetUnsuback = new LongAdder();
        // this.messageReceived = new LongAdder();
        // this.messageSent = new LongAdder();
        this.messageDropped = new LongAdder();
        this.messageQos0Received = new LongAdder();
        this.messageQos0Sent = new LongAdder();
        this.messageQos1Received = new LongAdder();
        this.messageQos1Sent = new LongAdder();
        this.messageQos2Received = new LongAdder();
        this.messageQos2Sent = new LongAdder();
        this.byteReceived = new LongAdder();
        this.byteSent = new LongAdder();
    }

    public static MqttMetrics getInstance() {
        if (instance == null) {
            synchronized (MqttMetrics.class) {
                if (instance == null) {
                    instance = new MqttMetrics();
                }
            }
        }

        return instance;
    }

    public LongAdder getPacketConnect() {
        return packetConnect;
    }

    public void setPacketConnect(LongAdder packetConnect) {
        this.packetConnect = packetConnect;
    }

    public LongAdder getPacketConnack() {
        return packetConnack;
    }

    public void setPacketConnack(LongAdder packetConnack) {
        this.packetConnack = packetConnack;
    }

    public LongAdder getPacketDisconnect() {
        return packetDisconnect;
    }

    public void setPacketDisconnect(LongAdder packetDisconnect) {
        this.packetDisconnect = packetDisconnect;
    }

    public LongAdder getPacketPingreq() {
        return packetPingreq;
    }

    public void setPacketPingreq(LongAdder packetPingreq) {
        this.packetPingreq = packetPingreq;
    }

    public LongAdder getPacketPingresp() {
        return packetPingresp;
    }

    public void setPacketPingresp(LongAdder packetPingresp) {
        this.packetPingresp = packetPingresp;
    }

    public LongAdder getPacketPublishReceived() {
        return packetPublishReceived;
    }

    public void setPacketPublishReceived(LongAdder packetPublishReceived) {
        this.packetPublishReceived = packetPublishReceived;
    }

    public LongAdder getPacketPublishSent() {
        return packetPublishSent;
    }

    public void setPacketPublishSent(LongAdder packetPublishSent) {
        this.packetPublishSent = packetPublishSent;
    }

    public LongAdder getPacketPubackReceived() {
        return packetPubackReceived;
    }

    public void setPacketPubackReceived(LongAdder packetPubackReceived) {
        this.packetPubackReceived = packetPubackReceived;
    }

    public LongAdder getPacketPubackSent() {
        return packetPubackSent;
    }

    public void setPacketPubackSent(LongAdder packetPubackSent) {
        this.packetPubackSent = packetPubackSent;
    }

    public LongAdder getPacketPubackMissed() {
        return packetPubackMissed;
    }

    public void setPacketPubackMissed(LongAdder packetPubackMissed) {
        this.packetPubackMissed = packetPubackMissed;
    }

    public LongAdder getPacketPubcompReceived() {
        return packetPubcompReceived;
    }

    public void setPacketPubcompReceived(LongAdder packetPubcompReceived) {
        this.packetPubcompReceived = packetPubcompReceived;
    }

    public LongAdder getPacketPubcompSent() {
        return packetPubcompSent;
    }

    public void setPacketPubcompSent(LongAdder packetPubcompSent) {
        this.packetPubcompSent = packetPubcompSent;
    }

    public LongAdder getPacketPubcompMissed() {
        return packetPubcompMissed;
    }

    public void setPacketPubcompMissed(LongAdder packetPubcompMissed) {
        this.packetPubcompMissed = packetPubcompMissed;
    }

    public LongAdder getPacketPubrecReceived() {
        return packetPubrecReceived;
    }

    public void setPacketPubrecReceived(LongAdder packetPubrecReceived) {
        this.packetPubrecReceived = packetPubrecReceived;
    }

    public LongAdder getPacketPubrecSent() {
        return packetPubrecSent;
    }

    public void setPacketPubrecSent(LongAdder packetPubrecSent) {
        this.packetPubrecSent = packetPubrecSent;
    }

    public LongAdder getPacketPubrecMissed() {
        return packetPubrecMissed;
    }

    public void setPacketPubrecMissed(LongAdder packetPubrecMissed) {
        this.packetPubrecMissed = packetPubrecMissed;
    }

    public LongAdder getPacketPubrelReceived() {
        return packetPubrelReceived;
    }

    public void setPacketPubrelReceived(LongAdder packetPubrelReceived) {
        this.packetPubrelReceived = packetPubrelReceived;
    }

    public LongAdder getPacketPubrelSent() {
        return packetPubrelSent;
    }

    public void setPacketPubrelSent(LongAdder packetPubrelSent) {
        this.packetPubrelSent = packetPubrelSent;
    }

    public LongAdder getPacketPubrelMissed() {
        return packetPubrelMissed;
    }

    public void setPacketPubrelMissed(LongAdder packetPubrelMissed) {
        this.packetPubrelMissed = packetPubrelMissed;
    }

    public LongAdder getPacketSubscribe() {
        return packetSubscribe;
    }

    public void setPacketSubscribe(LongAdder packetSubscribe) {
        this.packetSubscribe = packetSubscribe;
    }

    public LongAdder getPacketSuback() {
        return packetSuback;
    }

    public void setPacketSuback(LongAdder packetSuback) {
        this.packetSuback = packetSuback;
    }

    public LongAdder getPacketUnsubscribe() {
        return packetUnsubscribe;
    }

    public void setPacketUnsubscribe(LongAdder packetUnsubscribe) {
        this.packetUnsubscribe = packetUnsubscribe;
    }

    public LongAdder getPacketUnsuback() {
        return packetUnsuback;
    }

    public void setPacketUnsuback(LongAdder packetUnsuback) {
        this.packetUnsuback = packetUnsuback;
    }

    public LongAdder getMessageDropped() {
        return messageDropped;
    }

    public void setMessageDropped(LongAdder messageDropped) {
        this.messageDropped = messageDropped;
    }

    public LongAdder getMessageQos0Received() {
        return messageQos0Received;
    }

    public void setMessageQos0Received(LongAdder messageQos0Received) {
        this.messageQos0Received = messageQos0Received;
    }

    public LongAdder getMessageQos0Sent() {
        return messageQos0Sent;
    }

    public void setMessageQos0Sent(LongAdder messageQos0Sent) {
        this.messageQos0Sent = messageQos0Sent;
    }

    public LongAdder getMessageQos1Received() {
        return messageQos1Received;
    }

    public void setMessageQos1Received(LongAdder messageQos1Received) {
        this.messageQos1Received = messageQos1Received;
    }

    public LongAdder getMessageQos1Sent() {
        return messageQos1Sent;
    }

    public void setMessageQos1Sent(LongAdder messageQos1Sent) {
        this.messageQos1Sent = messageQos1Sent;
    }

    public LongAdder getMessageQos2Received() {
        return messageQos2Received;
    }

    public void setMessageQos2Received(LongAdder messageQos2Received) {
        this.messageQos2Received = messageQos2Received;
    }

    public LongAdder getMessageQos2Sent() {
        return messageQos2Sent;
    }

    public void setMessageQos2Sent(LongAdder messageQos2Sent) {
        this.messageQos2Sent = messageQos2Sent;
    }

    public LongAdder getByteReceived() {
        return byteReceived;
    }

    public void setByteReceived(LongAdder byteReceived) {
        this.byteReceived = byteReceived;
    }

    public LongAdder getByteSent() {
        return byteSent;
    }

    public void setByteSent(LongAdder byteSent) {
        this.byteSent = byteSent;
    }

}
