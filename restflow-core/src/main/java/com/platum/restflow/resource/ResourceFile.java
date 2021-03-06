package com.platum.restflow.resource;

import java.io.Serializable;

import org.apache.commons.lang3.Validate;
import org.junit.Ignore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.platum.restflow.utils.promise.PromiseHandler;
import com.platum.restflow.utils.promise.PromiseResult;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

public class ResourceFile implements Serializable {
	
	private static final long serialVersionUID = 4275505225311314919L;

	private Object id;
	
	private String fileName;
	
	private String resourceName;
	
	private boolean isNew = true;
	
	@JsonIgnore
	private ReadStream<Buffer> stream;
	
	@JsonIgnore
	private String path;
	
	@JsonIgnore
	private boolean uploaded;
	
	public Object id() {
		return id;
	}

	public ResourceFile id(Object id) {
		this.id = id;
		return this;
	}

	public String fileName() {
		return fileName;
	}

	public ResourceFile fileName(String fileName) {
		this.fileName = fileName;
		return this;
	}
	
	public String path() {
		return path;
	}

	public ResourceFile path(String path) {
		this.path = path;
		return this;
	}
	
	public String resourceName() {
		return resourceName;
	}

	public ResourceFile resourceName(String resourceName) {
		this.resourceName = resourceName;
		return this;
	}

	public boolean isNew() {
		return isNew;
	}

	public ResourceFile setNew(boolean isNew) {
		this.isNew = isNew;
		return this;
	}

	public ReadStream<Buffer> stream() {
		return stream;
	}

	public ResourceFile stream(ReadStream<Buffer> stream) {
		return stream(stream, null);
	}
			
	public ResourceFile stream(ReadStream<Buffer> stream, PromiseHandler<PromiseResult<Buffer>> handler) {
		Validate.notNull(stream);
		this.stream = stream;
		if(handler != null) {
			stream.exceptionHandler(err -> {
				handler.handle(new PromiseResult<>(err));
		    }).endHandler(buf -> {
				handler.handle(new PromiseResult<>(null));   	 
		    });	
		}
		return this;
	}

	public boolean uploaded() {
		return uploaded;
	}
	
	public ResourceFile uploaded(boolean uploaded) {
		this.uploaded = uploaded;
		return this;
	}

	@Override
	public String toString() {
		return "ResourceFile [id=" + id + ", fileName=" + fileName + ", resourceName=" + resourceName + ", isNew="
				+ isNew + ", stream=" + stream + ", path=" + path + ", uploaded=" + uploaded + "]";
	}

}
