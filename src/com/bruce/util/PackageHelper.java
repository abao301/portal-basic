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

package com.bruce.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 包资源访问帮助类 */
public class PackageHelper
{
	private static final char FILE_EXT_CHAR			= '.';
	private static final char PACKAGE_SEP_CHAR		= '.';
	private static final char PATH_SEP_CHAR			= '/';
	private static final char INNER_CLS_SEP_CHAR	= '$';
	private static final String FILE_PROTOCOL		= "file";
	private static final String JAR_PROTOCOL		= "jar";
	private static final String PATH_SEP_STR		= "/";
	private static final String EMPTY_STR			= "";
	private static final String CLASS_SUFFIX		= ".class";
	private static final int CLASS_SUFFIX_LEN		= CLASS_SUFFIX.length();
	
	/** 资源文件过滤器接口 */
	public static interface ResourceFilter
	{
		/** 过滤方法
		 *
		 * @param packagePath	: 包路径
		 * @param fileName		: 文件名称
		 * @return				  true -> 接受, false -> 不接受
		 */
		boolean accept(String packagePath, String fileName);
	}
	
	/** 文件类型过滤器（接受指定扩展名） */
	public static class FileTypeFilter implements ResourceFilter
	{
		private String suffix;
		
		public FileTypeFilter(String suffix)
		{
			int index = suffix.lastIndexOf(FILE_EXT_CHAR);
			
			if(index != -1)
				this.suffix = suffix.substring(index);
			else
				this.suffix = FILE_EXT_CHAR + suffix;
		}
		
		@Override
		public boolean accept(String packagePath, String fileName)
		{
			return fileName.endsWith(suffix);
		}
	}
	
	/** 文件类型过滤器（接受一组扩展名） */
	public static class FileTypesFilter implements ResourceFilter
	{
		private Set<String> suffixes = new HashSet<String>();
		
		public FileTypesFilter(String suffix1, String ... suffixN)
		{
			Set<String> suffixes = new HashSet<String>(suffixN.length + 1);
			suffixes.add(suffix1);
			suffixes.addAll(Arrays.asList(suffixN));
			
			setSuffixes(suffixes);
		}
		
		public FileTypesFilter(Set<String> suffixes)
		{
			setSuffixes(suffixes);
		}
		
		private void setSuffixes(Set<String> suffixes)
		{
			for(String suffix : suffixes)
			{
				int index = suffix.lastIndexOf(FILE_EXT_CHAR);
				
				if(index != -1)
					this.suffixes.add(suffix.substring(index));
				else
					this.suffixes.add(FILE_EXT_CHAR + suffix);
			}
		}

		@Override
		public boolean accept(String packagePath, String fileName)
		{
			int index = fileName.lastIndexOf(FILE_EXT_CHAR);
			
			if(index == -1)
				return false;
			
			String suffix = fileName.substring(index);
			
			return suffixes.contains(suffix);
		}
	}
	
	/** 顶层 Java class 文件过滤器（只接受 '*.class' 类型文件，并且文件名不包含 '$' 符号） */
	public static class TopLevelClassFileFilter extends FileTypeFilter
	{
		public TopLevelClassFileFilter()
		{
			super(CLASS_SUFFIX);
		}
		
		@Override
		public boolean accept(String packagePath, String fileName)
		{
			return super.accept(packagePath, fileName) && fileName.indexOf(INNER_CLS_SEP_CHAR) == -1;
		}
	}
	
	/** Java 类型过滤器接口 */
	public static interface ClassFilter
	{
		/** 过滤方法
		 *
		 * @param clazz	: Java 类型的 {@link Class} 对象
		 * @return		  true -> 接受, false -> 不接受
		 */
		boolean accept(Class<?> clazz);
	}
	
	/** 兼容类型过滤器（只接受与指定类型兼容的类型） */
	public static class CompatibleTypeFilter implements ClassFilter
	{
		private Class<?> clazz;
		
		public CompatibleTypeFilter(Class<?> clazz)
		{
			this.clazz = clazz;
		}
		
		@Override
		public boolean accept(Class<?> clazz)
		{
			return this.clazz.isAssignableFrom(clazz);
		}
	}
	
	/** 
	 * 获取包中某些类的 {@link Class} 对象集合（只获取顶层类）
	 * 
	 * @param basePackage		: 包名
	 * @param recursive			: 是否递归子包
	 * @return					     符合条件的 {@link Class} 对象集合
	 */
	public static final Set<Class<?>> getClasses(String basePackage, boolean recursive)
	{
		return getClasses(basePackage, recursive, (ClassFilter)null);
	}
	
	/** 
	 * 获取包中某些类的 {@link Class} 对象集合（只获取与 compatibleType 兼容的顶层类）
	 * 
	 * @param basePackage		: 包名
	 * @param recursive			: 是否递归子包
	 * @param compatibleType	: 兼容类型
	 * @return					     符合条件的 {@link Class} 对象集合
	 */
	public static final Set<Class<?>> getClasses(String basePackage, boolean recursive, Class<?> compatibleType)
	{
		return getClasses(basePackage, recursive, new CompatibleTypeFilter(compatibleType));
	}
	
	/** 
	 * 获取包中某些类的 {@link Class} 对象集合（只获取顶层类）
	 * 
	 * @param basePackage		: 包名
	 * @param recursive			: 是否递归子包
	 * @param classFilter		: 类型过滤器，参考：{@link PackageHelper.ClassFilter}
	 * @return					     符合条件的 {@link Class} 对象集合
	 */
	public static final Set<Class<?>> getClasses(String basePackage, boolean recursive, ClassFilter classFilter)
	{
		return getClasses(basePackage, recursive, classFilter, new TopLevelClassFileFilter());
	}
	
	/** 
	 * 获取包中某些类的 {@link Class} 对象集合
	 * 
	 * @param basePackage		: 包名
	 * @param recursive			: 是否递归子包
	 * @param classFilter		: 类型过滤器，参考：{@link PackageHelper.ClassFilter}
	 * @param resourceFilter	: 资源文件过滤器，参考：{@link PackageHelper.ResourceFilter}
	 * @return					     符合条件的 {@link Class} 对象集合
	 */
	public static final Set<Class<?>> getClasses(String basePackage, boolean recursive, ClassFilter classFilter, ResourceFilter resourceFilter)
	{
		Set<Class<?>> classes	= new HashSet<Class<?>>();
		String basePackagePath	= basePackage.replace(PACKAGE_SEP_CHAR, PATH_SEP_CHAR);
		Set<String> names		= getResourceNames(basePackagePath, recursive, resourceFilter);
		
		for(String name : names)
		{
			String className = name.substring(0, name.length() - CLASS_SUFFIX_LEN);
			className		 = className.replace(PATH_SEP_CHAR, PACKAGE_SEP_CHAR);
			Class<?> clazz	 = GeneralHelper.loadClass(className);
			
			if(clazz != null)
			{
				if(classFilter != null && !classFilter.accept(clazz))
					continue;
				
				classes.add(clazz);
			}
		}
		
		return classes;
	}
	
	/**
	 * 获取包中所有资源文件的名称集合
	 * 
	 * @param basePackagePath	: 包路径
	 * @param recursive			: 是否递归子目录
	 * @return					     符合条件的资源名称集合
	 */
	public static final Set<String> getResourceNames(String basePackagePath, boolean recursive)
	{
		return getResourceNames(basePackagePath, recursive, null);
	}
	
	/**
	 * 获取包中某些资源文件的名称集合
	 * 
	 * @param basePackagePath	: 包路径
	 * @param recursive			: 是否递归子目录
	 * @param filter			: 资源文件过滤器，参考：{@link PackageHelper.ResourceFilter}
	 * @return					     符合条件的资源名称集合
	 */
	public static final Set<String> getResourceNames(String basePackagePath, boolean recursive, ResourceFilter filter)
	{
		basePackagePath = adjustBasePackagePath(basePackagePath);
		
		Set<String> names = new HashSet<String>();
		List<URL> urlList = GeneralHelper.getClassResources(PackageHelper.class, basePackagePath);
		
		for(URL url : urlList)
		{
			String protocol	= url.getProtocol();
			
			if(protocol.equals(FILE_PROTOCOL))
			{
				String filePath			= CryptHelper.urlDecode(url.getPath());
				int packageStartIndex	= filePath.lastIndexOf(basePackagePath);
				
				scanResourceNamesByFile(filePath, packageStartIndex, recursive, filter, names);
			}
			else if(protocol.equals(JAR_PROTOCOL))
				scanResourceNamesByJar(url, basePackagePath, recursive, filter, names);
		}
		
		return names;
	}
	
	/**
	 * 在文件系统中扫描资源文件
	 * 
	 * @param filePath			: 文件目录
	 * @param packageStartIndex	: filePath 参数中包名称的起始位置
	 * @param recursive			: 是否扫描子目录
	 * @param names				: 扫描结果集合
	 */
	public static final void scanResourceNamesByFile(String filePath, final int packageStartIndex, final boolean recursive, Set<String> names)
	{
		scanResourceNamesByFile(filePath, packageStartIndex, recursive, null, names);
	}

	/**
	 * 在文件系统中扫描资源文件
	 * 
	 * @param filePath			: 文件目录
	 * @param packageStartIndex	: filePath 参数中包名称的起始位置
	 * @param recursive			: 是否扫描子目录
	 * @param filter			: 资源文件过滤器，参考：{@link PackageHelper.ResourceFilter}
	 * @param names				: 扫描结果集合
	 */
	public static final void scanResourceNamesByFile(String filePath, final int packageStartIndex, final boolean recursive, final ResourceFilter filter, Set<String> names)
	{
		final String basePackagePath = filePath.substring(packageStartIndex);

		File dir	 = new File(filePath);
		File[] files = dir.listFiles(new FileFilter()
                    		{
                    			@Override
                    			public boolean accept(File file)
                    			{
                    				if(file.isDirectory())
                    					return recursive;
                    				
                    				if(filter != null)
                    				{
                        				String fileName = file.getName();
                        				return filter.accept(basePackagePath, fileName);
                    				}
                    				
                    				return true;
                    			}
                    		});
		
		for(File file : files)
		{
			String name = file.getName();

			if(file.isDirectory())
			{
				String subPath = filePath.endsWith(PATH_SEP_STR) ?
								 filePath + name : filePath + PATH_SEP_CHAR + name;
				
				scanResourceNamesByFile(subPath, packageStartIndex, recursive, filter, names);
			}
			else
			{				
				if(basePackagePath.length() != 0)
					name = basePackagePath + PATH_SEP_CHAR + name;
				
				names.add(name);
			}
		}
	}
	
	/**
	 * 在 jar 文件中扫描资源文件
	 * 
	 * @param url				: jar 文件的 URL
	 * @param basePackagePath	: jar 文件中的包路径
	 * @param recursive			: 是否扫描子目录
	 * @param names				: 扫描结果集合
	 */
	public static final void scanResourceNamesByJar(URL url, String basePackagePath, final boolean recursive, Set<String> names)
	{
		scanResourceNamesByJar(url, basePackagePath, recursive, null, names);
	}

	/**
	 * 在 jar 文件中扫描资源文件
	 * 
	 * @param url				: jar 文件的 URL
	 * @param basePackagePath	: jar 文件中的包路径
	 * @param recursive			: 是否扫描子目录
	 * @param filter			: 资源文件过滤器，参考：{@link PackageHelper.ResourceFilter}
	 * @param names				: 扫描结果集合
	 */
	public static final void scanResourceNamesByJar(URL url, String basePackagePath, final boolean recursive, final ResourceFilter filter, Set<String> names)
	{
		basePackagePath = adjustBasePackagePath(basePackagePath);
		
		try
		{
			JarFile jar = ((JarURLConnection)url.openConnection()).getJarFile();
			Enumeration<JarEntry> entries = jar.entries();
			
			while(entries.hasMoreElements())
			{
				JarEntry entry	= entries.nextElement();
				String name		= entry.getName();
				int nameIndex	= name.lastIndexOf(PATH_SEP_CHAR);
				
				if(entry.isDirectory() || !name.startsWith(basePackagePath))
					continue;
				
				if(!recursive && nameIndex != basePackagePath.lastIndexOf(PACKAGE_SEP_CHAR))
					continue;
				
				String packagePath	= (nameIndex != -1 ? name.substring(0, nameIndex)  : EMPTY_STR);
				String simpleName	= (nameIndex != -1 ? name.substring(nameIndex + 1) : name);
				
				if(filter != null && !filter.accept(packagePath, simpleName))
					continue;
				
				names.add(name);
			}
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private static final String adjustBasePackagePath(String basePackagePath)
	{
		basePackagePath = GeneralHelper.safeTrimString(basePackagePath).replace(PACKAGE_SEP_CHAR, PATH_SEP_CHAR);
		
		if(basePackagePath.startsWith(PATH_SEP_STR))
			basePackagePath = basePackagePath.substring(1, basePackagePath.length());
		
		if(basePackagePath.endsWith(PATH_SEP_STR))
			basePackagePath = basePackagePath.substring(0, basePackagePath.length() - 1);
		return basePackagePath;
	}
}