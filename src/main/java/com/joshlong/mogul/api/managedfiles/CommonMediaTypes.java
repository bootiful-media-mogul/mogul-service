package com.joshlong.mogul.api.managedfiles;

import org.apache.tika.Tika;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@ImportRuntimeHints(CommonMediaTypesConfiguration.TikaHints.class)
class CommonMediaTypesConfiguration {

	static class TikaHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints( //
				@NonNull RuntimeHints hints, //
				@Nullable ClassLoader classLoader) {
			var prefix = "/org/apache/tika/mime/";
			for (var r : new String[] { //
					prefix + "tika-mimetypes.xml", //
					prefix + "custom-mimetypes.xml" //
			}) {
				var resource = new ClassPathResource(r);
				if (resource.exists()) {
					hints.resources().registerResource(resource);
				}
			}
		}

	}

}

/**
 * includes some useful {@link MediaType media types} that, for some reason,
 * {@link MediaType } does not have. Provides facilities for working with {@link MediaType
 * media types} and doing file type detection, based on Apache Tika's {@link Tika}.
 */
public abstract class CommonMediaTypes {

	public static final MediaType BINARY = MediaType.APPLICATION_OCTET_STREAM;

	// archives
	public static final MediaType ZIP = MediaType.parseMediaType("application/zip");

	public static final MediaType TGZ = MediaType.parseMediaType("application/gzip");

	// images

	// all images. will this work?
	public static final MediaType IMAGE = MediaType.parseMediaType("image/*");

	public static final MediaType PNG = MediaType.IMAGE_PNG;

	public static final MediaType WEBP = MediaType.parseMediaType("image/webp");

	public static final MediaType JPG = MediaType.IMAGE_JPEG;

	public static final MediaType GIF = MediaType.IMAGE_GIF; // yuck

	// movies
	public static final MediaType MP4 = MediaType.parseMediaType("application/mp4");

	// audio
	public static final MediaType WAV = MediaType.parseMediaType("audio/wav");

	public static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");

	// public static MediaType guess(Resource resource) {
	// var ct = java.net.URLConnection.guessContentTypeFromName(resource.getFilename());
	// var mt = StringUtils.hasText(ct) ? MediaType.parseMediaType(ct) :
	// CommonMediaTypes.BINARY;
	// log.debug("guessed [{}] for resource [{}]. the media type is [{}]", ct,
	// resource.getFilename(), mt);
	// return mt;
	// }

	private static final Tika TIKA = new Tika();

	public static MediaType guess(Resource resource) {
		try {
			return guess(resource.getInputStream());
		} //
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isVideo(MediaType resource) {
		return resource.isCompatibleWith(MediaType.parseMediaType("video/*"));
	}

	public static boolean isImage(MediaType mediaType) {
		return mediaType.isCompatibleWith(MediaType.parseMediaType("image/*"));
	}

	public static boolean isAudio(MediaType mediaType) {
		return mediaType.isCompatibleWith(MediaType.parseMediaType("audio/*"));
	}

	public static boolean isBinary(MediaType mediaType) {
		return !isText(mediaType);
	}

	public static boolean isText(MediaType mediaType) {
		return mediaType.isCompatibleWith(MediaType.TEXT_PLAIN);
	}

	// private InputStream from (Resource resource) {
	// try {
	// return resource.getInputStream() ;
	// } //
	// catch (IOException e) {
	// throw new RuntimeException(e);
	// }
	// }
	public static MediaType guess(InputStream inputStream) {
		try {
			return MediaType.parseMediaType(TIKA.detect(inputStream));
		} //
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isArchive(MediaType mt) {
		return mt.isCompatibleWith(CommonMediaTypes.ZIP) || mt.isCompatibleWith(CommonMediaTypes.TGZ);
	}

}
