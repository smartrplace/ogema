/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ogema.resourcemanager.impl.model;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_7;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

import org.objectweb.asm.Type;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.resourcemanager.InvalidResourceTypeException;
import org.ogema.resourcemanager.impl.ApplicationResourceManager;
import org.ogema.resourcemanager.impl.ResourceBase;
import org.ogema.resourcemanager.virtual.VirtualTreeElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jan Lapp, Fraunhofer IWES
 */
enum ResourceFactoryASM {
	
	INSTANCE;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<Class<? extends Resource>, Class<? extends ResourceBase>> implementationTypes = new HashMap<>();
    
    private final String BASECLASS_NAME = Type.getInternalName(ResourceBase.class);
	private final String BASECLASS_NAME_LIST = Type.getInternalName(DefaultResourceList.class);
    private final String CONSTRUCTOR_DESCRIPTOR = Type.getConstructorDescriptor(ResourceBase.class.getConstructors()[0]);

	ResourceFactoryASM() {
	}

	synchronized Class<? extends ResourceBase> getImplementation(Class<? extends Resource> ogemaType) {
		Class<? extends ResourceBase> implementationType = implementationTypes.get(ogemaType);
		if (implementationType != null) {
			return implementationType;
		}
		implementationType = createImplementationType(ogemaType);
		implementationTypes.put(ogemaType, implementationType);
		return implementationType;
	}
    
	@SuppressWarnings("rawtypes")
	private Class<? extends ResourceBase> createImplementationType(final Class<? extends Resource> ogemaType) {
		final long startTime = System.currentTimeMillis();

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		MethodVisitor mv;
        
		final String classname = "resourceImpl."+ogemaType.getCanonicalName();
		final String baseClassName = (ResourceList.class.isAssignableFrom(ogemaType))
				? BASECLASS_NAME_LIST
				: BASECLASS_NAME;
        
		cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER,
				classname.replace('.', '/'), null,
				baseClassName,
				new String[]{Type.getInternalName(ogemaType)});

		{//constructor
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", CONSTRUCTOR_DESCRIPTOR, null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitMethodInsn(INVOKESPECIAL, baseClassName, "<init>", CONSTRUCTOR_DESCRIPTOR, false);
			mv.visitInsn(RETURN);
			mv.visitMaxs(4, 4);
			mv.visitEnd();
		}
		
		// filter out duplicate names
		List<String> methodNamesList = new ArrayList<>();
        
        for (Method m : ogemaType.getMethods()) {
            if (m.getDeclaringClass().equals(Resource.class) | m.getDeclaringClass().equals(ResourceList.class)) {
				continue;
			}
            
            if ((m.getModifiers() & Modifier.ABSTRACT) == 0) {
                //Java 8 default method, skip
                continue;
            }
			
			if (methodNamesList.contains(m.getName())) {
				continue;
			}
            
			String signature = null;
			if (ResourceList.class.isAssignableFrom(m.getReturnType())){
				java.lang.reflect.Type type = m.getGenericReturnType();
				Class<?> typeParameter = (Class) ((ParameterizedType)type).getActualTypeArguments()[0];

				//XXX use ASM to generate signatures?
				signature = String.format("()L%s<L%s;>;",
						Type.getInternalName(m.getReturnType()),
						Type.getInternalName(typeParameter));
			}
			
            int access = ACC_PUBLIC | ((m.isBridge() || m.isSynthetic()) ? ACC_SYNTHETIC : 0);

			mv = cw.visitMethod(access, m.getName(),
					Type.getMethodDescriptor(m),
					signature, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitLdcInsn(m.getName());
			mv.visitMethodInsn(INVOKEVIRTUAL,
					baseClassName,
					"getSubResource",
					"(Ljava/lang/String;)Lorg/ogema/core/model/Resource;", false);
			mv.visitInsn(ARETURN);
			mv.visitMaxs(2, 1);
			mv.visitEnd();
			
			methodNamesList.add(m.getName());
		}

		cw.visitEnd();

		final byte[] bytes = cw.toByteArray();

		final ClassLoader cl = ResourceBase.class.getClassLoader();

		return AccessController.doPrivileged(new PrivilegedAction<Class<? extends ResourceBase>>() {
			@Override
			public Class<? extends ResourceBase> run() {
				logger.debug("created implementation class for type {} ({} bytes, {} ms)",
						ogemaType, bytes.length, System.currentTimeMillis()-startTime);
				return new MyClassLoader(cl).define(classname, bytes, ogemaType.getProtectionDomain());
			}
		});
	}
    
    // custom class loader to call defineClass without reflection
    private class MyClassLoader extends ClassLoader {
        
        MyClassLoader(ClassLoader p) {
            super(p);
        }
        
        Class<? extends ResourceBase> define(String classname, byte[] bytes, ProtectionDomain pd) {
            @SuppressWarnings("unchecked")
            Class<? extends ResourceBase> implementationClass = (Class<? extends ResourceBase>) super.defineClass(classname, bytes, 0, bytes.length, pd);
            return implementationClass;
        }
        
    }
    
    @SuppressWarnings("unchecked")
	public <T extends ResourceBase> T makeResource(VirtualTreeElement el, String path, ApplicationResourceManager resman) {
		try {
			Class<? extends ResourceBase> impl = getImplementation(el.getType());
			Constructor<?> constr = impl.getConstructor(VirtualTreeElement.class, String.class, ApplicationResourceManager.class);
			return (T) constr.newInstance(new Object[]{el, path, resman});
		} catch (IllegalAccessException | IllegalArgumentException |
				InstantiationException | NoSuchMethodException |
				SecurityException | InvocationTargetException ex) {
			throw new InvalidResourceTypeException("Code generation failed", ex);
		}
	}

}
