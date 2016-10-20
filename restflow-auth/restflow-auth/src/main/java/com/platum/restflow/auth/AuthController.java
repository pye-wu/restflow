package com.platum.restflow.auth;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platum.restflow.Restflow;
import com.platum.restflow.RestflowEnvironment;
import com.platum.restflow.RestflowHttpMethod;
import com.platum.restflow.components.Controller;
import com.platum.restflow.exceptions.ResflowObjectConversionException;
import com.platum.restflow.exceptions.RestflowException;
import com.platum.restflow.exceptions.RestflowInvalidRequestException;
import com.platum.restflow.resource.Resource;
import com.platum.restflow.resource.ResourceMethod;
import com.platum.restflow.resource.ResourceObject;
import com.platum.restflow.utils.promise.PromiseHandler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class AuthController implements Controller {
	
	public static final String AUTH_RESOURCE_NAME_PROPERTY = "restflow.auth.resourceName";
	
	public static final String AUTH_RESOURCE_VERSION_PROPERTY = "restflow.auth.resourceVersion";
	
	public static final String AUTHENTICATE_METHOD = "authenticate";
	
	public static final String CHANGE_PASSWORD_METHOD = "changePassword";
	
	public static final String USER_INFO_METHOD = "userInfo";
	
	public static final String REVOKE_METHOD = "revoke";
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Router router;
	
	private AuthManager manager;
	
	private Resource authResource;
	
	public AuthController() {
		manager = AuthFactory.getAuthManager();
	}
	
	@Override
	public void setRouter(Router router) {
		this.router = router;
	}
	
	@Override
	public void setRestflow(Restflow restflow) {
		RestflowEnvironment env = restflow.getEnvironment();
		String resourceName = env.getProperty(AUTH_RESOURCE_NAME_PROPERTY);
		String resourceVersion = env.getProperty(AUTH_RESOURCE_VERSION_PROPERTY);
		if(StringUtils.isEmpty(resourceName)) {
			if(logger.isWarnEnabled()) {
				logger.warn("Resource Name not provided. Auth Manager cannot be used.");
			}
		} else {
			try {
				authResource = StringUtils.isEmpty(resourceVersion)
						   ? restflow.getResource(resourceName)
						   : restflow.getResource(resourceName, resourceVersion);	
				if(!authResource.isInternal()) {
					throw new RestflowException("Cannot use resource ["+authResource.getName()+
												"] as auth resource because it's not internal.");
				}
				manager.config(restflow, authResource);
			} catch(Throwable e) {
				logger.error("Cannot start Auth Controller due to exception.", e);
			}
		}
	}

	@Override
	public void createRoutes() {
		if(manager.authApplies()) {
			publishAuthenticate();
			publishChangePassword();
			publishUserInfo();
			publishRevoke();	
		}
	}
	
	private void publishAuthenticate() {
		ResourceMethod method = authResource.getMethod(AUTHENTICATE_METHOD);
		if(method != null) {
			String url = RestflowHttpMethod.POST.parseUrl(method.getUrl());
			router.route(url).handler(BodyHandler.create());
			router.route(url).handler(routingContext -> {
					  manager.authenticate(method, resolveAuthDetails(routingContext))
					  .success(obj -> {
						  manager.getAuthorization(obj)
						  .success(auth -> {
							  HttpServerResponse response = routingContext.response();
							  response
							  .headers()
							  .add(AuthFilter.AUTH_HEADER, auth);
							  response.setStatusCode(HttpResponseStatus.OK.code())
					  		  .end(new JsonObject().put("id_token", auth)
					  				  			   .put("token_type", AuthFilter.BEARER_AUTH_HEADER)
					  				  			   .toString());	
						  })
						  .error(error -> {
				  			 routingContext.response()
				  			 .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
				  			 .end(getErrorJson(error));	
						  });
					  })
					  .error(error -> {
			  			 routingContext.response()
			  			 .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
			  			 .end(getErrorJson(error));	
					  });
				  });
			if(logger.isInfoEnabled()) {
				logger.info("Autentication method with url "+method.getUrl()+" deployed.");
			}
		}
	}
	
	private String getErrorJson(Throwable error) {
		return new JsonObject().put("error", error.getMessage())
			.encode();
	}

	private void publishChangePassword() {
		ResourceMethod method = authResource.getMethod(CHANGE_PASSWORD_METHOD);	
		if(method != null) {
			String url = RestflowHttpMethod.POST.parseUrl(method.getUrl());
			router.route(url).handler(BodyHandler.create());
			router.route(url).handler(routingContext -> {
					  resolveAuth(routingContext, handler -> {
						  AuthDetails auth = resolveAuthDetails(routingContext);			
						  manager.authenticate(authResource.getMethod(AUTHENTICATE_METHOD), auth)
				  		  .success(aObj -> {
				  			 manager.changePassword(method, aObj, auth)
				  			 .success(Void -> {
					  			 routingContext.response()
					  			 .setStatusCode(HttpResponseStatus.OK.code())
					  			 .end();				  				 
				  			 })
				  			 .error(error -> {
					  			 routingContext.response()
					  			 .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
					  			 .end(getErrorJson(error));			  				 
				  			 });
				  		  })
				  		  .error(error -> {
				  			 routingContext.response()
				  			 .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
				  			 .end(getErrorJson(error));
				  		  });									  
					  }); 
				  });
			if(logger.isInfoEnabled()) {
				logger.info("Change password method with url "+method.getUrl()+" deployed.");
			}
		}
	}
	
	private void publishUserInfo() {
		ResourceMethod method = authResource.getMethod(USER_INFO_METHOD);
		if(method != null) {
			router.route(RestflowHttpMethod.GET.parseUrl(method.getUrl()))
			.handler(routingContext -> {
				resolveAuth(routingContext, obj -> {
				  	 routingContext.response()
		  			 .setStatusCode(HttpResponseStatus.OK.code())
		  			 .end(Json.encode(obj));						
				});
			});
			if(logger.isInfoEnabled()) {
				logger.info("Revoke method with url "+method.getUrl()+" deployed.");
			}
		}
	}
	
	private void publishRevoke() {
		ResourceMethod method = authResource.getMethod(REVOKE_METHOD);
		if(method != null) {		
			router.route(RestflowHttpMethod.GET.parseUrl(method.getUrl()))
				  .handler(routingContext -> {
					  manager.revoke(method, routingContext.request()
							  					.getHeader(AuthFilter.AUTH_HEADER))
					  .success(obj -> {
						  	 routingContext.response()
				  			 .setStatusCode(HttpResponseStatus.OK.code())
				  			 .end();							  
					  })
					  .error(error -> {
				  			 routingContext.response()
				  			 .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
				  			 .end(getErrorJson(error));			  				 
			  		  });				  		 
				  });
			if(logger.isInfoEnabled()) {
				logger.info("Revoke method with url "+method.getUrl()+" deployed.");
			}
		}
	}
	
	private void resolveAuth(RoutingContext routingContext, PromiseHandler<ResourceObject> handler) {
		 String auth = routingContext.request().getHeader(AuthFilter.AUTH_HEADER);
		 manager.resolveAuthorization(auth)
		 .success(obj -> {
			  if(handler != null) {
				  handler.handle(obj);
			  }
		 })
		 .error(error -> {
			  routingContext.response()
			  .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
			  .end(getErrorJson(error));
		 });
	}
	
	private AuthDetails resolveAuthDetails(RoutingContext routingContext) {
		String json = routingContext.getBodyAsString();
		if(json != null) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				return (AuthDetails) mapper.readValue(json, AuthDetails.class);
			} catch (Throwable e) {
				throw new ResflowObjectConversionException(
						"It was impossible to convert request data to object(s).", e);
			}
		}
		throw new RestflowInvalidRequestException("Request body is empty.");
	}
	
}
