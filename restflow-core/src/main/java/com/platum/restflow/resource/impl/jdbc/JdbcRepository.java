package com.platum.restflow.resource.impl.jdbc;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sql2o.Connection;
import org.sql2o.Query;
import org.sql2o.ResultSetHandler;
import org.sql2o.ResultSetIterable;

import com.platum.restflow.AuthMetadata;
import com.platum.restflow.exceptions.InvalidQueryFieldException;
import com.platum.restflow.exceptions.ResflowNotExistsException;
import com.platum.restflow.exceptions.RestflowDuplicatedRefException;
import com.platum.restflow.exceptions.RestflowFieldConversionValidationException;
import com.platum.restflow.resource.Params;
import com.platum.restflow.resource.Resource;
import com.platum.restflow.resource.ResourceMetadata;
import com.platum.restflow.resource.ResourceMethod;
import com.platum.restflow.resource.ResourceObject;
import com.platum.restflow.resource.ResourceRepository;
import com.platum.restflow.resource.annotation.QueryBuilderImpl;
import com.platum.restflow.resource.impl.AbstractResourceComponent;
import com.platum.restflow.resource.property.ResourceProperty;
import com.platum.restflow.resource.query.QueryField;
import com.platum.restflow.resource.query.impl.JdbcQueryBuilder;
import com.platum.restflow.resource.transaction.RepositoryTransaction;
import com.platum.restflow.resource.transaction.impl.JdbcTransation;

@QueryBuilderImpl(JdbcQueryBuilder.class)
public class JdbcRepository<T> extends AbstractResourceComponent<T> implements ResourceRepository<T> {
	
	private final  Logger logger = LoggerFactory.getLogger(getClass());
	
	private JdbcClient jdbcClient;
	
	private RepositoryTransaction<Connection> contextTransaction;
	
	private AuthMetadata authorization;
	
	public JdbcRepository(ResourceMetadata<T> metadata) {
		super(metadata);
		jdbcClient = JdbcClient.createInstance(metadata.resource().getDatasource(), 
							metadata.datasource().getProperties());
	}

	@Override
	public void close() {
		if(jdbcClient != null) {
			jdbcClient.close();
		}
	}

	@Override
	public T get(ResourceMethod method, Params params) {
		Connection connection = resolveConnection();
		Query query = addQueryParams(connection.createQuery(method.getQuery()),
				method.getParams(), params);
		T object = null;
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try(ResultSetIterable<T> iterable = 
				query.executeAndFetchLazy(new JdbcResourceObjectMapper<T>(metadata))) {
			Iterator<T> iterator = iterable.iterator();
			if(iterator.hasNext()) {
				object = iterator.next();
				if(iterator.hasNext()) {
					throw new RestflowDuplicatedRefException(
							"More than one object found for get request.");		
				}
				return object;
			} else {
				throw new ResflowNotExistsException("Object not found.");
			}
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}
	}

	@Override
	public List<ResourceObject> find(ResourceMethod method, Params params, QueryField... fields) {
		Connection connection = resolveConnection();
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try {
			Query query = addQueryParams(connection.createQuery(method.getQuery()),
					method.getParams(), params);
			ResultSetHandler<ResourceObject> mapper = new JdbcResourceObjectMapper<ResourceObject>(metadata, fields);
			return query.executeAndFetch(mapper);
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}
	}

	@Override
	public long count(ResourceMethod method, Params params) {
		Connection connection = resolveConnection();
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try {
			Query query = addQueryParams(connection.createQuery(method.getQuery()),
					method.getParams(), params);
			return query.executeScalar(Long.class);
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}
	}

	@Override
	public T insert(ResourceMethod method, T object) {
		return insert(method, object, null);
	}
	
	@Override
	public T insert(ResourceMethod method, T object, Params params) {
		Connection connection = resolveConnection();
		Resource resource = metadata.resource();
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try {
			addQueryParams(
					connection.createQuery(method.getQuery()),
					method.getParams(), object, params)
				.executeUpdate();
			if(resource.isIdAutoGenerated()) {
				Object id = connection.getKey();
				setIdProperty(object, resource.getIdPropertyAsObject(), id);
			}
			return object;
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}		
	}

	@Override
	public T update(ResourceMethod method, T object) {
		return update(method, object, null);
	}
	
	@Override
	public T update(ResourceMethod method, T object, Params params) {
		Connection connection = resolveConnection();
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try {
			addQueryParams(
					connection.createQuery(method.getQuery()),
					method.getParams(), object, params)
				.executeUpdate();
			return object;
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}
	}
	
	@Override
	public void delete(ResourceMethod method, T object) {
		Connection connection = resolveConnection();
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try {
			addQueryParams(
					connection.createQuery(method.getQuery()),
					method.getParams(), object, null)
				.executeUpdate();
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}
	}
	
	@Override
	public void delete(ResourceMethod method, Params params) {
		Connection connection = resolveConnection();
		if(logger.isInfoEnabled()) {
			logger.info("Executing query "+method.getQuery());
		}
		try {
			addQueryParams(
					connection.createQuery(method.getQuery()),
					method.getParams(), params)
				.executeUpdate();
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			closeConnectionIfNecessary(connection);
		}
	}

	@Override
	public void batchUpdate(ResourceMethod method, List<T> objects) {
		boolean txGenerated = false;
		RepositoryTransaction<Connection> trans = contextTransaction;
		if(trans == null) {
			trans = newTransaction();
			txGenerated = true;
		}
		final Connection connection = trans.connection();
		if(logger.isInfoEnabled()) {
			logger.info("Executing batch query "+method.getQuery());
		}
		try {
			Query query = connection.createQuery(method.getQuery());
			objects.stream().forEach(object -> {
				addQueryParams(query,
						method.getParams(), object, null)
				.addToBatch();
			});
			query.executeBatch();
			if(txGenerated) {
				trans.commit();
				trans = null;
			}
		} catch(Throwable e) {
			throw jdbcClient.translateException(e);
		} finally {
			if(txGenerated && trans != null) {
				trans.rollback();
				trans = null;
			} 			
			closeConnectionIfNecessary(connection);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E> RepositoryTransaction<E> newTransaction() {
		if(logger.isInfoEnabled()) {
			logger.info("Starting new transaction");
		}
		Connection connection = jdbcClient.template().beginTransaction();
		return (RepositoryTransaction<E>) new JdbcTransation().connection(connection);		
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E> ResourceRepository<T> withTransaction(RepositoryTransaction<E> transaction) {
		this.contextTransaction = (RepositoryTransaction<Connection>) transaction;
		return this;
	}
	
	@Override
	public boolean hasTransationSupport() {
		return true;
	}

	@Override
	public Resource resource() {
		return metadata.resource();
	}

	@Override
	public ResourceRepository<T> withAuthorization(AuthMetadata auth) {
		this.authorization = auth;
		return this;
	}

	private Connection resolveConnection() {
		if(contextTransaction != null) {
			return contextTransaction.connection();
		} else {
			return jdbcClient.template().open();
		}
	}
	
	private void closeConnectionIfNecessary(Connection connection) {
		if(contextTransaction == null) {
			connection.close();
		}
	}
	
	private Query addQueryParams(final Query query, String[] paramNames, Params params) {
		Params usedParams = new Params();
		if(paramNames != null && paramNames.length > 0) {
			Map<String, Object> paramsMap = params.getParams();
			try {
				Stream.of(paramNames)
				.forEach(param -> {
					if(authorization == null || !param.startsWith(AuthMetadata.AUTH_PARAMS_PREFIX)) {
						Object value = paramsMap.get(param);
						query.addParameter(param, value);	
						usedParams.addParam(param, value);
					}
					else {
						Object value =  authorization.get(param.replace(AuthMetadata.AUTH_PARAMS_PREFIX, ""));
						query.addParameter(param, value);
						usedParams.addParam(param, value);
					} 
				});	
			} catch(Throwable e) {
				throw new InvalidQueryFieldException(e.getMessage());
			}

		}
		if(logger.isDebugEnabled()) {
			logger.debug("Using: "+usedParams);
		}
		return query;
	}
	
	private Query addQueryParams(Query query, String[] params, T object, Params extParams) {
		Params usedParams = new Params();
		if(params != null && params.length > 0) {
			try {
				Stream.of(params)
				.forEach(param -> {
					try {
						if(authorization == null || !param.startsWith(AuthMetadata.AUTH_PARAMS_PREFIX)) {
							Object value  = object instanceof ResourceObject 
											? ((ResourceObject) object).getProperty(param)
											: FieldUtils.readField(object, param, true);
							query.addParameter(param, value);
							usedParams.addParam(param, value);
						}
						else {
							Object value =  authorization.get(param.replace(AuthMetadata.AUTH_PARAMS_PREFIX, ""));
							query.addParameter(param, value);
							usedParams.addParam(param, value);
						}
					} catch(Throwable e) {
						String error = "Exception occured when getting param ["+param+"] value";
						if(logger.isDebugEnabled()) {
							logger.debug(error, e);
						}
						throw new RestflowFieldConversionValidationException(error);					
					}					  
				});				
			} catch(Throwable e) {
				throw new InvalidQueryFieldException(e.getMessage());
			}
		}
		if(extParams != null && !extParams.isEmpty()) {
			Map<String, Object> paramsMap = extParams.getParams();
			paramsMap.entrySet().stream()
			.forEach(param -> {
				String key = param.getKey();
				if(!usedParams.containsKey(key)) {
					Object value = param.getValue();
					query.addParameter(key, value);
					usedParams.addParam(key, value);
				}
			});
		}
		if(logger.isDebugEnabled()) {
			logger.debug("Using: "+usedParams);
		}
		return query;
		
	}
	
	private void setIdProperty(T object, ResourceProperty property, Object value) {
		try {
			if(object instanceof ResourceObject) {
				 ((ResourceObject) object).setIdProperty(property)
				 						  .setId(value);
			} else {
				FieldUtils.writeField(object, property.getName(), value);
			}		
		} catch(Throwable e) {
			String error = "Exception occured when converting field ["+property+"] with value ["+value+"]";
			if(logger.isDebugEnabled()) {
				logger.debug(error, e);
			}
			throw new RestflowFieldConversionValidationException(error);
		}
	}

}
