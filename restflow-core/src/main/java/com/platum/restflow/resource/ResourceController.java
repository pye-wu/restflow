package com.platum.restflow.resource;

import java.io.File;
import java.net.URLConnection;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;
import com.platum.restflow.RestflowDefaultConfig;
import com.platum.restflow.RestflowEnvironment;
import com.platum.restflow.RestflowHttpMethod;
import com.platum.restflow.RestflowRoute;
import com.platum.restflow.exceptions.RestflowException;
import com.platum.restflow.resource.impl.AbstractResourceComponent;
import com.platum.restflow.utils.promise.Promise;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

/**
 * TODO refactor to use RestflowController
 * */
public class ResourceController<T> extends AbstractResourceComponent<T>{
	
	private final Logger logger = LoggerFactory.getLogger(getClass()); 
	
	private Resource resource;
	
	private ResourceFileSystem fileSystem;
	
	private String tmpPath;
	
	public ResourceController(ResourceMetadata<T> metadata) {
		super(metadata);
		resource = metadata.resource();
		if(StringUtils.isNotEmpty(resource.getFileSystem())) {
			fileSystem = ResourceFactory.getFileSystemInstance(metadata);
		}
		resolveTempPath();
	}
				
	public ResourceController<T> get(RestflowRoute route, ResourceMethod method) {
		if(RestflowHttpMethod.GET.equalValue(method.getUrl())) {
			route
			.httpMethod(RestflowHttpMethod.GET, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
							  .getContext()
							  .getLangRequestFromRequest(routingContext.request()))
				.find(method, helper.getFilterFromRequest(), helper.getModifierFromRequest())
				.success(data -> { 
					helper.end(HttpResponseStatus.OK, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			});			
		} else if(RestflowHttpMethod.GET_WITH_ID.equalValue(method.getUrl())) {
			route.httpMethod(RestflowHttpMethod.GET_WITH_ID, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()))
				.get(method, new Params().addParam(metadata.idPropertyName()
								, helper.getRequestIdParam()))
				.success(data -> { 
					helper.end(HttpResponseStatus.OK, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			});			
		} else {
			route.httpMethod(RestflowHttpMethod.GET, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);
				ResourceService<T> service = helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()));
				Params params = helper.getParamsFromRequest();
				Promise<?> promise = method.isCollection() ?
						service.find(method, helper.getFilterFromRequest(), 
								helper.getModifierFromRequest(), params) : 
						service.get(method, params);
				promise.success(data -> { 
					helper.end(HttpResponseStatus.OK, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			});
		}
		return this;
	}

	public ResourceController<T> post(RestflowRoute route, ResourceMethod method) {
		if(RestflowHttpMethod.POST.equalValue(method.getUrl())) {
			route.httpMethod(RestflowHttpMethod.POST, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);			
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()))
				.insert(method, helper.getRequestResourceObject())
				.success(data -> { 
					helper.end(HttpResponseStatus.CREATED, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			});
		} else {
			route.httpMethod(RestflowHttpMethod.POST, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);									   
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()))
				.insert(method, helper.getRequestResourceObject(), helper.getParamsFromRequest())
				.success(data -> { 
					helper.end(HttpResponseStatus.CREATED, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			});
		}
			
		return this;
	}

	public ResourceController<T> put(RestflowRoute route, ResourceMethod method) {
		if(RestflowHttpMethod.PUT_WITH_ID.equalValue(method.getUrl())) {
			route.httpMethod(RestflowHttpMethod.PUT_WITH_ID, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);		
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()))
				.update(method, metadata.setObjectId(
									helper.getRequestResourceObject(), helper.getRequestIdParam()))
				.success(data -> { 
					helper.end(HttpResponseStatus.OK, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			})	;		
		} else {
			route.httpMethod(RestflowHttpMethod.PUT,routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);		
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()))
				.update(method, metadata.setObjectId(helper.getRequestResourceObject(), 
									helper.getRequestIdParam()), helper.getParamsFromRequest())
				.success(data -> { 
					helper.end(HttpResponseStatus.OK, data);
				})
				.error(error -> {
					helper.fail(error);
				});
			});
		}
		return this;
	}

	public ResourceController<T> patch(RestflowRoute route, ResourceMethod method) {
		route
		.httpMethod(RestflowHttpMethod.PATCH, routingContext -> {
			ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);		
			helper.logRequest(method)
			.service()
			.withLang(metadata.restflow()
					  .getContext()
					  .getLangRequestFromRequest(routingContext.request()))
			.partialUpdate(method, metadata.setObjectId(helper.getRequestResourceObject(), 
															helper.getRequestIdParam()),
							helper.getParamsFromRequest())
			.success(data -> { 
				helper.end(HttpResponseStatus.OK, data);
			})
			.error(error -> {
				helper.fail(error);
			});
		});
		return this;
	}

	public ResourceController<T> delete(RestflowRoute route, ResourceMethod method) {
		if(RestflowHttpMethod.DELETE_WITH_ID.equalValue(method.getUrl())) {
			route.httpMethod(RestflowHttpMethod.DELETE_WITH_ID, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);	
				final ResourceService<T> service = helper.logRequest(method)
														.service()
														.withLang(metadata.restflow()
																  .getContext()
																  .getLangRequestFromRequest(routingContext.request()));
				Object id = helper.getRequestIdParam();
				service.delete(id)
				.success(v -> {
					if(fileSystem != null) {
						fileSystem.destroy(id)
									.allways(req -> helper.end(HttpResponseStatus.OK));
					} else {
						helper.end(HttpResponseStatus.OK);	
					}					
				})
				.error(helper::fail);
			});			
		} else {
			route.httpMethod(RestflowHttpMethod.DELETE, routingContext -> {
				ResourceHttpHelper<T> helper = new ResourceHttpHelper<T>(metadata, routingContext);		
				helper.logRequest(method)
				.service()
				.withLang(metadata.restflow()
						  .getContext()
						  .getLangRequestFromRequest(routingContext.request()))
				.delete(method, helper.getParamsFromRequest())
				.success(v -> { 
					helper.end(HttpResponseStatus.OK);
				})
				.error(error -> {
					helper.fail(error);
				});
			});
		} 
		return this;
	}
	
	public ResourceController<T> upload(RestflowRoute route, UploadMethod uploadMethod) {
		RestflowHttpMethod httpMethod = RestflowHttpMethod.UPLOAD;
		route.httpMethod(httpMethod, routingContext -> {
			if(logger.isInfoEnabled()) {
				logger.info("Upload request for resource [" +
							resource.getName() + 
							"] with path " + 
							httpMethod.value());
			}
			HttpServerRequest req = routingContext.request();
			HttpServerResponse res = routingContext.response();
			String id = req.getParam(RestflowDefaultConfig.DEFAULT_ID_PARAM);
			if(fileSystem == null) {
				routingContext.fail(new RestflowException("Filesystem not found."));
			} else if(StringUtils.isEmpty(id)) {
				routingContext.fail(new RestflowException("Invalid id provided."));
			} else {
				req.setExpectMultipart(true)				
			       .uploadHandler(upload -> {
					  String uploadedFileName = new File(tmpPath, UUID.randomUUID().toString()).getPath();
					  upload.streamToFileSystem(uploadedFileName);
			          upload.exceptionHandler(routingContext::fail)
			          		.endHandler(v -> {
			          		fileSystem.save(new ResourceFile()
											.id(id)
											.uploaded(true)
											.path(uploadedFileName)
											.resourceName(uploadMethod.getUseResource())
											.fileName(upload.filename()))
			          		.success(s -> {
					        	  res.setChunked(true)
					        	  	 .setStatusCode(HttpResponseStatus.OK.code())
					        	  	 .end();			          			
			          		}).error(err -> {
					        	  routingContext.fail(err);
					        });
			          });
			        });
			}
		});
		return this;
	}
	
	public ResourceController<T> download(RestflowRoute route, DownloadMethod download) {
		RestflowHttpMethod httpMethod =RestflowHttpMethod.DOWNLOAD;
		route.httpMethod(httpMethod, routingContext -> {
			HttpServerRequest req = routingContext.request();
			HttpServerResponse res = routingContext.response();
			if(logger.isInfoEnabled()) {
				logger.info("Download request for resource [" +
							resource.getName() + 
							"] with path " + 
							httpMethod.value());
			}
			String id = req.getParam(RestflowDefaultConfig.DEFAULT_ID_PARAM);
			if(fileSystem == null) {
				routingContext.fail(new RestflowException("Filesystem not found."));
			} else if(StringUtils.isEmpty(id)) {
				routingContext.fail(new RestflowException("Invalid id provided."));
			} else {
				fileSystem.get(id)
				.success(file -> {
					String fileName = req.getParam("filename"); 
					String contentType = "application/octet-stream"; 
					if(StringUtils.isEmpty(fileName)) {
						fileName = StringUtils.isEmpty(file.fileName()) 
									? "download" : file.fileName();;
					}
					try {
					    String proposedContentType = URLConnection.guessContentTypeFromName(file.fileName());
					    if(StringUtils.isNotEmpty(proposedContentType)) {
					    	contentType = proposedContentType;
					    }
					} catch(Throwable e) {}
					try {
						fileName = new String(fileName.getBytes("UTF-8"), "ISO-8859-1");
					} catch(Throwable e) {}
					res.putHeader(HttpHeaders.CONTENT_TYPE, contentType)
							.putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName +"\";")
					        .putHeader(HttpHeaders.TRANSFER_ENCODING, "chunked")
					        .sendFile(file.path());
					/*ReadStream<Buffer> readStream = file.stream();
					readStream.endHandler(s -> {
						String contentType = "application/octet-stream"; 
						try {
						    String proposedContentType = URLConnection.guessContentTypeFromName(file.fileName());
						    if(StringUtils.isNotEmpty(proposedContentType)) {
						    	contentType = proposedContentType;
						    }
						} catch(Throwable e) {}
						res.putHeader("Content-Type", contentType)
						.putHeader("Content-Disposition", "attachment; filename=\"" + file.fileName() +"\";")
   						.setStatusCode(HttpResponseStatus.OK.code())
   						.end();
					}).exceptionHandler(routingContext::fail);
					res.setChunked(true);
					Pump p = Pump.pump(file.stream(), res);
					p.start();	*/				
				})
				.error(routingContext::fail);		
			}
		});
		return this;
	}
			
	@Override
	public void close() {
		//TODO log yourself
	}

	private void resolveTempPath() {
		tmpPath = metadata.restflow()
				  .getEnvironment()
				  .getProperty(RestflowEnvironment.TMP_PATH_PROPERTY);
		if(StringUtils.isEmpty(tmpPath)) {
			tmpPath = "./tmp/";
		} else if(!tmpPath.endsWith("/")) {
			tmpPath += "/";
		}
		File file = new File(tmpPath);
		if(!file.exists()) {
			file.mkdirs();
		}
	}
	
}