package io.rsocket.examples.starter.speed.configuration;

import java.util.Collections;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeType;

class RSocketRequesterSetupUtility {


	static boolean isCoreCodec(Object codec) {
		return codec.getClass().getPackage().equals(StringDecoder.class.getPackage());
	}

	static MimeType getMimeType(Decoder<?> decoder) {
		MimeType mimeType = decoder.getDecodableMimeTypes().get(0);
		return mimeType.getParameters().isEmpty() ? mimeType : new MimeType(mimeType, Collections.emptyMap());
	}

	static MimeType getDataMimeType(RSocketStrategies strategies) {
		// First non-basic Decoder (e.g. CBOR, Protobuf)
		for (Decoder<?> candidate : strategies.decoders()) {
			if (!isCoreCodec(candidate) && !candidate.getDecodableMimeTypes().isEmpty()) {
				return getMimeType(candidate);
			}
		}
		// First core decoder (e.g. String)
		for (Decoder<?> decoder : strategies.decoders()) {
			if (!decoder.getDecodableMimeTypes().isEmpty()) {
				return getMimeType(decoder);
			}
		}
		throw new IllegalArgumentException("Failed to select data MimeType to use.");
	}
}
