package org.fakereplace.manip;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;

import org.fakereplace.boot.Logger;
import org.fakereplace.manip.data.StaticFieldAccessRewriteData;

public class StaticFieldManipulator
{

   Map<String, Set<StaticFieldAccessRewriteData>> staticMethodData = new ConcurrentHashMap<String, Set<StaticFieldAccessRewriteData>>();

   public void clearRewrite(String className)
   {
      staticMethodData.remove(className);
   }

   /**
    * rewrites static field access to the same field on another class
    * 
    * @param oldClass
    * @param newClass
    * @param fieldName
    */
   public void rewriteStaticFieldAccess(String oldClass, String newClass, String fieldName, ClassLoader classLoader)
   {
      Set<StaticFieldAccessRewriteData> d = staticMethodData.get(oldClass);
      if (d == null)
      {
         d = new HashSet<StaticFieldAccessRewriteData>();
         staticMethodData.put(oldClass, d);
      }
      d.add(new StaticFieldAccessRewriteData(oldClass, newClass, fieldName, classLoader));
   }

   public void transformClass(ClassFile file)
   {
      if (staticMethodData.isEmpty())
      {
         return;
      }
      Map<Integer, StaticFieldAccessRewriteData> fieldAccessLocations = new HashMap<Integer, StaticFieldAccessRewriteData>();
      Map<StaticFieldAccessRewriteData, Integer> newFieldClassPoolLocations = new HashMap<StaticFieldAccessRewriteData, Integer>();
      Map<StaticFieldAccessRewriteData, Integer> newFieldAccessLocations = new HashMap<StaticFieldAccessRewriteData, Integer>();
      ConstPool pool = file.getConstPool();
      for (int i = 1; i < pool.getSize(); ++i)
      {
         if (pool.getTag(i) == ConstPool.CONST_Fieldref)
         {

            String className = pool.getFieldrefClassName(i);
            if (staticMethodData.containsKey(className))
            {
               String fieldName = pool.getFieldrefName(i);
               for (StaticFieldAccessRewriteData data : staticMethodData.get(className))
               {
                  if (fieldName.equals(data.getFieldName()))
                  {
                     fieldAccessLocations.put(i, data);
                     // we have found a field access
                     // now lets replace it
                     if (!newFieldClassPoolLocations.containsKey(data))
                     {
                        // we have not added the new class reference or
                        // the new call location to the class pool yet
                        int newCpLoc = pool.addClassInfo(data.getNewClass());
                        newFieldClassPoolLocations.put(data, newCpLoc);
                        // we do not need to change the name and type
                        int newNameAndType = pool.getFieldrefNameAndType(i);
                        newFieldAccessLocations.put(data, pool.addFieldrefInfo(newCpLoc, newNameAndType));
                     }
                     break;
                  }

               }
            }
         }
      }
      // this means we found an instance of the static field access
      if (!newFieldClassPoolLocations.isEmpty())
      {
         List<MethodInfo> methods = file.getMethods();
         for (MethodInfo m : methods)
         {
            try
            {
               if (m.getCodeAttribute() == null)
               {
                  continue;
               }
               CodeIterator it = m.getCodeAttribute().iterator();
               while (it.hasNext())
               {
                  int index = it.next();
                  int op = it.byteAt(index);
                  if (op == CodeIterator.GETSTATIC || op == CodeIterator.PUTSTATIC)
                  {
                     int val = it.s16bitAt(index + 1);
                     if (fieldAccessLocations.containsKey(val))
                     {
                        StaticFieldAccessRewriteData data = fieldAccessLocations.get(val);
                        it.write16bit(newFieldAccessLocations.get(data), index + 1);
                     }
                  }
               }
               m.getCodeAttribute().computeMaxStack();
            }
            catch (Exception e)
            {
               Logger.log(this, "Bad byte code transforming " + file.getName());
               e.printStackTrace();
            }
         }
      }
   }

}
