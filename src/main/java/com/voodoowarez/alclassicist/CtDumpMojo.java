package com.voodoowarez.alclassicist;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 *
 * @goal alclassiscist
 * 
 * @phase process-sources
 */
@Mojo(name = "ctdump", defaultPhase=LifecyclePhase.GENERATE_RESOURCES)
public class CtDumpMojo extends AbstractMojo
{
	/**
	* Where to install the results
	*/
	@Parameter(defaultValue= "${project.build.directory}/generated-alclassiscist/")
	protected String outputDirectory;

	/**
	* Files to process
	*/
	@Parameter
	protected String[] includes = new String[]{"*.class"};

	/**
	* Directory with source classes
	*/
	@Parameter(defaultValue= "${project.build.outputDirectory}")
	protected String sourceDirectory;

	/**
	* Resident Javassist ClassPool
	*/
	protected ClassPool classPool = ClassPool.getDefault();

	/**
	* Resident ObjectMapper
	*/
	protected ObjectMapper objectMapper = new ObjectMapper();

	protected int classTrim = ".class".length();
	protected String ctSuffix = ".ct.json";

	public void execute() throws MojoExecutionException
	{
		// look for files
		final Collection<File> classFiles = fetchFiles();
		if(classFiles.size() == 0){
			throw new MojoExecutionException("No matched files to process");
		}

		// create a class pool
		try {
			this.classPool.insertClassPath(this.sourceDirectory);
		} catch (NotFoundException e) {
			throw new MojoExecutionException("Could not add classpath "+this.sourceDirectory, e);
		}

		// retrieve all classes
		for(File classFile : classFiles){
			final String klassName = fetchClassnameFromFile(classFile);
			final CtClass klass;
			try{
				klass = this.classPool.get(klassName);
			}catch (NotFoundException e){
				throw new MojoExecutionException("Failed to class file "+classFile.getName() + " as "+klassName, e);
			}
			writeClass(klass);
		}
	}

	protected Collection<File> fetchFiles(){
		final Set<File> foundFiles = new HashSet<File>();
		final File sourceDirFile = new File(this.sourceDirectory);
		for(String includePattern : this.includes){
			final IOFileFilter filterPattern = new RegexFileFilter(includePattern);
			final Collection<File> matches = FileUtils.listFiles(sourceDirFile, filterPattern, TrueFileFilter.INSTANCE);
			foundFiles.addAll(matches);
		}
		return foundFiles;
	}

	protected String fetchClassnameFromFile(final File file) throws MojoExecutionException{
		try{
			final File parent = file.getParentFile();
			final String fullPath = file.getCanonicalPath();
			return fullPath.substring(parent.getCanonicalPath().length(), fullPath.length()-this.classTrim);
		}catch(IOException e){
			throw new MojoExecutionException("Bad file", e);
		}
	}

	protected void writeClass(final CtClass klass) throws MojoExecutionException{
		final String klassName = klass.getName(),
		  pathedName = this.outputDirectory+klassName.replace(".","/")+this.ctSuffix;
		final File file = new File(pathedName);
		try{
			FileUtils.forceMkdir(file.getParentFile());
		}catch(IOException e){
			throw new MojoExecutionException("Couldn't create directory "+file.getParent()+" for "+klassName, e);
		}
		try{
			this.objectMapper.writeValue(file, klass);
		}catch(Exception e){
			throw new MojoExecutionException("Couldn't output CtClass "+klassName, e);
		}
	}
}
