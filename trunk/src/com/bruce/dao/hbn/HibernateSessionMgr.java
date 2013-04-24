/*
 * Copyright Bruce Liang (ldcsaa@gmail.com)
 *
 * Version	: 3.1.1
 * Author	: Bruce Liang
 * Porject	: https://code.google.com/p/portal-basic
 * Bolg		: http://www.cnblogs.com/ldcsaa
 * WeiBo	: http://weibo.com/u/1402935851
 * QQ Group	: http://qun.qq.com/#jointhegroup/gid/75375912
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bruce.dao.hbn;

import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.jdbc.Work;

import com.bruce.dao.AbstractSessionMgr;
import com.bruce.dao.SessionMgr;
import com.bruce.dao.TransIsoLevel;
import com.bruce.util.GeneralHelper;

/**
 * 
 * @author Kingfisher
 * 
 * 功能：封装Session和Transaction对象的生命周期管理方法，主要功能：
 * 	1、加载 Hibernate 配置文件
 * 	2、安全打开 / 关闭 Session 对象
 * 	3、简化 Session 对象和 Transaction 的管理
 *
 * 异常处理：初始化失败时抛出 InvalidParameterException
 * 
 * 使用方法：
 * 	1、在自己的程序中使用本类提供的静态方法安全获取 / 关闭 Session 对象
 * 	2、可以使用本类的封装方法简化 Session对象和 Transaction 对象操作
 * 	3、建议在应用程序启动时调用初始化方法 initialize(...)，确定数据库连接配置无误
 * 
 */
public class HibernateSessionMgr extends AbstractSessionMgr<Session>
{
	/** 默认 Hibernate 配置文件 */
	public static final String DEFAULT_CONFIG_FILE = "hibernate.cfg.xml";
	/** Session Factory 工厂对象 */
	private SessionFactory sessionFactory;
	
	/** 
	 * 初始化 
	 * 
	 * @param args [0]	: hbn_cfg_file （默认：{@link HibernateSessionMgr#DEFAULT_CONFIG_FILE}）
	 * @throws InvalidParameterException
	 * @throws HibernateException
	 * 
	*/
	@Override
	public void initialize(String ... args)
	{
		if(args.length == 0)
			initialize();
		else if(args.length == 1)
			initialize(args[0]);
		else
			throw new InvalidParameterException("HibernateSessionMgr initialize fail (invalid paramers)");
	}
	
	/**
	 * 
	 * 使用默认的 Hibernate 配置文件配置 {@link SessionFactory}
	 * 
	 * @throws HibernateException	: 配置失败时抛出该异常
	 * 
	 */
	public void initialize()
	{
		initialize(DEFAULT_CONFIG_FILE);
	}

	/**
	 * 
	 * 使用特定的 Hibernate 配置文件配置 {@link SessionFactory}
	 * 
	 * @param hbn_cfg_file			: 配置文件路径 
	 * @throws HibernateException	: 配置失败时抛出该异常
	 * 
	 */
	public void initialize(String hbn_cfg_file)
	{
		configFile = GeneralHelper.isStrNotEmpty(hbn_cfg_file) ? hbn_cfg_file : DEFAULT_CONFIG_FILE;
		
		try
		{
			loadDefalutTransIsoLevel();
		}
		catch(HibernateException e)
		{
			unInitialize();
			throw e;
		}
	}
	
	/**
	 * 
	 * 关闭 {@link SessionFactory}
	 * 
	 * @throws HibernateException	: 操作失败时抛出该异常
	 * 
	 */
	@Override
	public void unInitialize()
	{
		if(sessionFactory != null)
		{
			synchronized(this)
			{
				if(sessionFactory != null)
				{
					sessionFactory.close();
					sessionFactory = null;
				}
			}
		}
	}

	/** 参考：{@link AbstractSessionMgr#loadDefalutTransIsoLevel()} */
	@Override
	protected void loadDefalutTransIsoLevel()
	{
		try
		{
			Session session = getSession();
			session.doWork(new Work()
			{
				@Override
				public void execute(Connection connection) throws SQLException
				{
					int level = connection.getTransactionIsolation();
					defaultTransIsoLevel = TransIsoLevel.fromInt(level);
				}
			});
		}
		finally
		{
			closeSession();
		}
	}
	
	/** 参考：{@link SessionMgr#setSessionTransIsoLevel(TransIsoLevel)} */
	@Override
	public void setSessionTransIsoLevel(final TransIsoLevel level)
	{
		Session session = getSession();
		session.doWork(new Work()
		{
			@Override
			public void execute(Connection connection) throws SQLException
			{
				connection.setTransactionIsolation(level.toInt());
			}
		});
	}
	
	/**
	 * 
	 * 获取配置文件
	 * 
	 */
	public String getConfigFile()
	{
		return configFile;
	}

	/**
	 * 
	 * 获取当前的 {@link SessionFactory} 实例
	 * 
	 */
	public final SessionFactory getSessionFactory()
	{
		return sessionFactory;
	}

	/**
	 * 
	 * 获取绑定到当前线程的 {@link Session} 实例
	 * 
	 * @throws HibernateException	: 获取 {@link Session} 实例失败时抛出该异常
	 * 
	 */
	@Override
	public final Session getSession()
	{
		Session session = localSession.get();

		if(session == null || !session.isOpen())
		{
			if(sessionFactory == null)
			{
				synchronized(this)
				{
					if(sessionFactory == null)
						sessionFactory = buildSessionFactory();
				}
			}
			
			session = sessionFactory.openSession();
			localSession.set(session);
		}

		return session;
	}
	
	// Hibernate 4.x 加上下面的注解
	@SuppressWarnings("deprecation")
	protected SessionFactory buildSessionFactory()
	{
		return new Configuration().configure(configFile).buildSessionFactory();
	}
	
	/**
	 * 
	 * 关闭当前线程的 {@link Session} 实例
	 * 
	 */
	@Override
	public final void closeSession()
	{
		Session session = localSession.get();
		localSession.set(null);

		if(session != null)
		{
			session.close();
		}
	}
	
	/**
	 * 
	 * 获取的 {@link Session} 对应的 {@link Transaction} 实例
	 * 
	 */
	public final Transaction getTransaction()
	{
		Session session = getSession();
		return session != null ? session.getTransaction() : null;
	}
	
	/**
	 * 
	 * 开始新事务
	 * 
	 */
	@Override
	public final void beginTransaction()
	{
		Transaction trans = getTransaction();
		
		if(trans != null)
			trans.begin();
	}
	
	/**
	 * 
	 * 提交事务
	 * 
	 */
	@Override
	public final void commit()
	{
		Transaction trans = getTransaction();
		
		if(trans != null)
			trans.commit();
	}
	
	/**
	 * 
	 * 回滚事务
	 * 
	 */
	@Override
	public final void rollback()
	{
		Transaction trans = getTransaction();
		
		if(trans != null)
			trans.rollback();
	}
}
