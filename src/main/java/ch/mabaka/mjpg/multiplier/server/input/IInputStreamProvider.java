package ch.mabaka.mjpg.multiplier.server.input;

import java.io.InputStream;

public interface IInputStreamProvider {
    InputStream getInputStream() throws Exception;
}