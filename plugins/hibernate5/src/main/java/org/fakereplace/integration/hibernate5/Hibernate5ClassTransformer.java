/*
 * Copyright 2012, Stuart Douglas, and individual contributors as indicated
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.fakereplace.integration.hibernate5;

import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;

import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import org.fakereplace.transformation.FakereplaceTransformer;

/**
 * Class transformer that intercepts invocations of the HibernatePersistence create* methods.
 *
 * @author Stuart Douglas
 */
public class Hibernate5ClassTransformer implements FakereplaceTransformer {

    public static final String PROXY_NAME = "org.fakereplace.integration.hibernate5.FakereplaceEntityManagerFactoryProxy";

    @Override
    public boolean transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final ClassFile file) throws IllegalClassFormatException, BadBytecode {
        if (file.getName().equals("org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl")) {
            for (MethodInfo method : (List<MethodInfo>) file.getMethods()) {
                if (method.getName().equals("build")) {

                    //need to save the method params so we can re-use them when we re-create our EMF
                    Bytecode s = new Bytecode(file.getConstPool());

                    //we need to interceptor the return value
                    //and add in our own bytecode fragment.
                    //first lets create our proxy creation code
                    final Bytecode b = new Bytecode(file.getConstPool());
                    b.addNew(PROXY_NAME);
                    b.add(Opcode.DUP_X1);
                    b.add(Opcode.SWAP);
                    b.addAload(0);
                    b.addInvokespecial(PROXY_NAME, "<init>", "(Lorg/hibernate/jpa/HibernateEntityManagerFactory;Lorg/hibernate/jpa/boot/spi/EntityManagerFactoryBuilder;)V");
                    insertBeforeReturn(method, s, b);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private void insertBeforeReturn(final MethodInfo method, final Bytecode s, final Bytecode b) throws BadBytecode {
        final CodeIterator itr = method.getCodeAttribute().iterator();
        itr.insert(s.get());
        while (itr.hasNext()) {
            final int pos = itr.next();
            int opcode = itr.byteAt(pos);
            if (opcode == Opcode.ARETURN) {
                itr.insert(pos, b.get());
            }
        }
        method.getCodeAttribute().computeMaxStack();
    }
}
