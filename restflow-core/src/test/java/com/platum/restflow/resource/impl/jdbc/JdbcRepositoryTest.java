package com.platum.restflow.resource.impl.jdbc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.sql2o.Connection;

import com.platum.restflow.Restflow;
import com.platum.restflow.exceptions.ResflowNotExistsException;
import com.platum.restflow.exceptions.RestflowDuplicatedRefException;
import com.platum.restflow.resource.Params;
import com.platum.restflow.resource.Resource;
import com.platum.restflow.resource.ResourceFactory;
import com.platum.restflow.resource.ResourceMethod;
import com.platum.restflow.resource.ResourceObject;
import com.platum.restflow.resource.transaction.RepositoryTransaction;



public class JdbcRepositoryTest {
	
	private static final List<String> SQL = new ArrayList<>();
	
	static {
	    SQL.add("drop table if exists test;");
	    SQL.add("create table test (id int generated by default as identity (start with 1 increment by 1) not null, name varchar(50) unique, s_name varchar(50));");
	    SQL.add("insert into test (name, s_name) values ('john', 'doe');");
	    SQL.add("insert into test (name, s_name) values ('jane', 'doe');");
	}
	
	private static JdbcRepository<ResourceObject> repository;
	
	private static Restflow restflow;
	
	private static Resource resource;
	
	static {
		try {
			Class.forName("org.hsqldb.jdbcDriver");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
		  
	private static Properties config() {
		Properties properties = new Properties();
		properties.put("jdbcUrl", "jdbc:hsqldb:mem:test?shutdown=true");
		properties.put("username", "SA");
		properties.put("password", "SA");
		properties.put("driverClassName", "org.hsqldb.jdbcDriver");
		//properties.addParam("dataSourceClassName", "org.hsqldb.jdbc.JDBCDataSource");
		//properties.addParam("dataSourceProperties.serverN", value)
		return properties;
	}
	
	private static void prepareDb() {
		JdbcClient client = JdbcClient.createInstance("test", config());
		Connection connection = client.template().open();
		for(String sql : SQL) {
			connection.createQuery(sql)
					  .executeUpdate();
		}
		connection.close();
	}

	private static void resolveRepository() {
		if(repository == null) {
			prepareDb();
			restflow = new Restflow()
					.loadModels();
			resource = restflow.getResource("resource_test");
			repository = new JdbcRepository<>(
					ResourceFactory.getResourceMetadataInstance(restflow, resource));
		}
	}
	
	@Test
	public void testGet() {
		resolveRepository();
		ResourceObject object = repository.get(resource.getMethod("getById"), 
				new Params()
				  .addParam("id", 1));
		Assert.assertEquals("john", object.getProperty("name"));
	}
	
	@Test(expected=ResflowNotExistsException.class)
	public void testGetNotFound() {
		resolveRepository();
		repository.get(resource.getMethod("getById"), 
				new Params()
				  .addParam("id", 5));		
	}
	
	@Test(expected=RestflowDuplicatedRefException.class)
	public void testGetDuplicated() {
		resolveRepository();
		repository.get(resource.getMethod("get"), 
				new Params()
				  .addParam("id", 1));	
	}
	
	@Test
	public void testFind() {
		resolveRepository();
		List<ResourceObject> objects = repository.find(resource.getMethod("get"), 
				new Params());
		Assert.assertEquals(2, objects.size());
	}
	
	@Test
	public void testFindWithParams() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("get").clone();
		String query = method.getQuery() + " where name = :name";
		method.setQuery(query)
			  .setParams(new String[]{"name"});
		List<ResourceObject> objects = repository.find(method, 
				new Params()
				  .addParam("name","john"));
		Assert.assertEquals(new Integer(1), (Integer) objects.get(0).getId());
	}
	
	@Test
	public void testCount() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("get").clone();
		String query = "select count(*) from (" + method.getQuery() + ")";
		method.setQuery(query);
		Assert.assertEquals(2, repository.count(method, 
								new Params()));		
	}
	
	@Test
	public void testCountWithParams() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("get").clone();
		String query = "select count(*) from (" + method.getQuery() + ") where name = :name";
		method.setQuery(query)
			  .setParams(new String[]{"name"});
		Assert.assertEquals(1, repository.count(method, 
								new Params()
								  .addParam("name","jane")));				
	}
	
	@Test
	public void testInsert() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("insert");
		ResourceObject object = repository.insert(method, new ResourceObject()
									.setString("name", "joja")
									.setString("surname", "doe"));
		Assert.assertNotNull(object.getId());
	}
	
	/*
	@Test(expected=RestflowDuplicatedRefException.class)
	public void testInsertDuplicated() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("insert");
		repository.insert(method, new ResourceObject()
									.setString("name", "joja")
									.setString("surname", "doe"));		
	} */
	
	@Test
	public void testUpdate() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("update");
		repository.update(method, 
				repository.get(resource.getMethod("getById"), 
								new Params()
								  .addParam("id", 2))
									.setString("name", "Jane"));		
		Assert.assertEquals("Jane", repository.get(resource.getMethod("getById"), 
									new Params()
									  .addParam("id", 2))
										.getString("name"));
	}
	
	@Test(expected=ResflowNotExistsException.class)
	public void testDelete() {
		resolveRepository();
		ResourceMethod method = resource.getMethod("delete");
		repository.delete(method, 
				repository.get(resource.getMethod("getById"), 
								new Params()
								  .addParam("id",1)));		
		repository.get(resource.getMethod("getById"), 
									new Params()
									  .addParam("id","1"));		
	}	

	@Test
	public void testBatchUpdate() {
		resolveRepository();
		repository.batchUpdate(resource.getMethod("insert"), Arrays.asList(
			new ResourceObject()
					.setString("name", "batchTest1")	
					.setString("surname", "testSurname1"),	
			new ResourceObject()
					.setString("name", "batchTest2")	
					.setString("surname", "testSurname2"),	
			new ResourceObject()
					.setString("name", "batchTest3")	
					.setString("surname", "testSurname3")					
				
		));
		ResourceMethod method = resource.getMethod("get").clone();
		String query = "select count(*) from (" + method.getQuery() +  " where name like :name)";
		method.setQuery(query)
			  .setParams(new String[]{"name"});
		Assert.assertEquals(3, repository.count(method, 
								new Params()
								  .addParam("name","batchTest%")));							
	}
	
	@Test
	public void testTransaction() {
		resolveRepository();
		RepositoryTransaction<Connection> trans = repository.newTransaction();
		ResourceObject object = new ResourceObject()
								.setString("name", "transTest");
		repository.withTransaction(trans)
				  .insert(resource.getMethod("insert"), object);
		object.setString("surname", "Test surname");
		repository.update(resource.getMethod("update"), object);
		trans.commit();
		ResourceMethod count = resource.getMethod("getById").clone();
		count.setQuery("select count(*) from ("+count.getQuery()+")"); 
		Assert.assertEquals(1, repository.count(count, 
				new Params()
				  .addParam("id",object.getId())));		
	}
	
	@Test
	public void testTransactionRollback() {
		resolveRepository();
		RepositoryTransaction<Connection> trans = repository.newTransaction();
		ResourceObject object = new ResourceObject()
								.setString("name", "transTest2");
		repository.withTransaction(trans)
				  .insert(resource.getMethod("insert"), object);
		object.setString("surname", "Test surname");
		repository.update(resource.getMethod("update"), object);
		trans.rollback();
		ResourceMethod count = resource.getMethod("getById").clone();
		count.setQuery("select count(*) from ("+count.getQuery()+")"); 
		Assert.assertEquals(0, repository.count(count, 
				new Params()
				  .addParam("id", object.getId())));
	}

}
