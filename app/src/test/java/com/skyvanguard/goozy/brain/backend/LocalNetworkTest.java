package com.skyvanguard.goozy.brain.backend;

import org.junit.Test;

import static org.junit.Assert.*;

public class LocalNetworkTest {

    @Test
    public void privateRanges_areLocal() {
        assertTrue(LocalNetwork.isLocalUrl("http://192.168.0.10:11434/v1"));
        assertTrue(LocalNetwork.isLocalUrl("http://10.0.2.2:11434/v1"));
        assertTrue(LocalNetwork.isLocalUrl("http://172.16.0.1/v1"));
        assertTrue(LocalNetwork.isLocalUrl("http://172.31.255.255/v1"));
        assertTrue(LocalNetwork.isLocalUrl("http://127.0.0.1:8080"));
        assertTrue(LocalNetwork.isLocalUrl("http://100.64.0.5:11434/v1"));
        assertTrue(LocalNetwork.isLocalUrl("http://localhost:1234/v1"));
        assertTrue(LocalNetwork.isLocalUrl("http://mi-pc.local:11434/v1"));
    }

    @Test
    public void publicAddresses_areNotLocal() {
        assertFalse(LocalNetwork.isLocalUrl("http://8.8.8.8/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://172.32.0.1/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://172.15.0.1/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://192.169.0.1/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://100.128.0.1/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://api.example.com/v1"));
    }

    @Test
    public void lookalikes_areNotLocal() {
        assertFalse(LocalNetwork.isLocalUrl("http://192.168.0.10.evil.com/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://10.0.0.1.example.org/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://localhost.evil.com/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://evil.com/?h=192.168.0.1"));
        assertFalse(LocalNetwork.isLocalUrl("http://192.168.0.1@evil.com/v1"));
    }

    @Test
    public void malformedInput_isNotLocal() {
        assertFalse(LocalNetwork.isLocalUrl(null));
        assertFalse(LocalNetwork.isLocalUrl(""));
        assertFalse(LocalNetwork.isLocalUrl("no es una url"));
        assertFalse(LocalNetwork.isLocalUrl("http://999.1.1.1/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://10.0.0/v1"));
        assertFalse(LocalNetwork.isLocalUrl("http://10..0.1/v1"));
    }
}
