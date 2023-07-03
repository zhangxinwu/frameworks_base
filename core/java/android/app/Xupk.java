package android.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.StringBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.lang.reflect.Constructor;
import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import android.app.Application;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import android.util.Log;
import android.util.LogPrinter;
import java.util.Map;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.annotation.Nullable;

public class Xupk
{
    class FileInfo
    {
        public String fileName;
        public List<ClassInfo> classList=new ArrayList<>();
    }
    public class ClassInfo
    {
        
        @SuppressLint("MutableBareField")
        public @Nullable String className;

        @SuppressLint("all")
        public final Map<String,Object> methodMap = new HashMap<String,Object>();
    }

    public static @Nullable Object getFieldOjbect( @Nullable String class_name, @Nullable Object obj, @Nullable String filedName)
    {
        try
        {
            Class obj_class = Class.forName(class_name);
            Field field = obj_class.getDeclaredField(filedName);
            field.setAccessible(true);
            return field.get(obj);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }
    
    public static @Nullable ClassLoader getClassLoader()
    {
        ClassLoader resultClassloader = null;
        try
        {
            Class class_ActivityThread = Class.forName("android.app.ActivityThread");
            Method method = class_ActivityThread.getMethod("currentActivityThread", new Class[]{});
            Object currentActivityThread = method.invoke(null, new Object[]{});

            Object mBoundApplication = getFieldOjbect(
                    "android.app.ActivityThread",
                    currentActivityThread,
                    "mBoundApplication"
            );
            Object loadedApkInfo = getFieldOjbect(
                    "android.app.ActivityThread$AppBindData",
                    mBoundApplication,
                    "info"
            );
            Application mApplication = (Application) getFieldOjbect(
                    "android.app.LoadedApk",
                    loadedApkInfo,
                    "mApplication"
            );
            resultClassloader = mApplication.getClassLoader();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return resultClassloader;
    }


    public static @Nullable Method getMethod( @Nullable ClassLoader appClassLoader, @Nullable String className, @Nullable String methodName)
    {
        Class class_DexFileClazz = null;
        try
        {
            class_DexFileClazz = appClassLoader.loadClass(className);
            for (Method method : class_DexFileClazz.getDeclaredMethods())
            {
                if (method.getName().equals(methodName))
                {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    static Method method_native_fakeInvoke = null;
    /** 
     * 对集合里的每一个方法发起主动调用
     */
    public static void fakeInvoke( @Nullable Object method)
    {
        try
        {
            // 该方法为native方法,声明于Xupk.java,实现于dalvik_system_DexFile.cc 
             // 用于对指定函数发起主动调用
            if(method_native_fakeInvoke==null)
            {
                method_native_fakeInvoke=getMethod(getClassLoader(),"android.app.Xupk","native_fakeInvoke");
            }   
            if(method==null)
            {
                return;
            }
            method_native_fakeInvoke.invoke(null, method);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        
    }

    /**
     * 获取指定类里所有的方法
     * 返回类的方法数量
     */
    public static int loadAllMethodsWithClass( @Nullable Class klass, @Nullable ClassInfo classInfo)
    {
        //Log.i("XUPK", "ActivityThread:loadAllMethods from class:" + className);
        int count=0;
        try
        {
            if (klass == null)
            {
                return 0;
            }
            
            // 获取目标类的所有构造函数
            Constructor constructors[] = klass.getDeclaredConstructors();          
            for (Object constructor : constructors)
            {
                
                String methodName=klass.getName()+constructor.toString();
                classInfo.methodMap.put(methodName,constructor);
                count++;
            }

            // 获取目标类的所有成员函数
            Method[] methods = null;
            // try {
            //     methods = klass.getMethods();
            //     for (Method method : methods)
            //     {
            //         String methodName=klass.getName()+method.toString();
            //         classInfo.methodMap.put(methodName,method);
            //         count++;
            //     }
            // } catch (Error | Exception e){ }
            try {
                methods = klass.getDeclaredMethods();
                for (Method method : methods)
                {
                    String methodName=klass.getName()+method.toString();
                    classInfo.methodMap.put(methodName,method);
                    count++;
                }
            } catch(Error | Exception e){ }

        }
        catch (Error | Exception e)
        {
            e.printStackTrace();      
        } 
        return count;
    }     

  
       
    private static String formatTime(long ms) {
        int ss = 1000;
        int mi = ss * 60;
        int hh = mi * 60;
        int dd = hh * 24;

        long day = ms / dd;
        long hour = (ms - day * dd) / hh;
        long minute = (ms - day * dd - hour * hh) / mi;
        long second = (ms - day * dd - hour * hh - minute * mi) / ss;
        long milliSecond = ms - day * dd - hour * hh - minute * mi - second * ss;

        String strHour = hour < 10 ? "0" + hour : "" + hour;//小时
        String strMinute = minute < 10 ? "0" + minute : "" + minute;//分钟
        String strSecond = second < 10 ? "0" + second : "" + second;//秒
        String strMilliSecond = milliSecond < 10 ? "0" + milliSecond : "" + milliSecond;//毫秒
        strMilliSecond = milliSecond < 100 ? "0" + strMilliSecond : "" + strMilliSecond;
        return strHour+":"+strMinute + ":" + strSecond+",";//+strMilliSecond ;
    }

    @SuppressLint("VisiblySynchronized")
    static Thread thread=null;

    @SuppressLint("VisiblySynchronized")
    public synchronized void xupkThread()
    {      
        Method unpackAppMethod = getMethod(getClassLoader(), "android.app.Xupk", "unpackAppFlag");
        if(thread==null)
        {
            thread=new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    String configPath="data/local/tmp/xk.config";
                    while(true)
                    {
                        try
                        {
                            Thread.sleep(1000);
                            String strConfig=readFileString(configPath);
                            if(strConfig!=null)
                            {
                                Log.e("XUPK", "Found configration:"+strConfig);
                                // 配置文件格式:
                                // com.package.name [method_info.json]
                                Log.e("XUPK", "Start xupk");
                                long startMillis = System.currentTimeMillis();

                                strConfig=strConfig.replace("\n","");
                                String[] configs = strConfig.split(" ");
                                if(configs.length==1)
                                {
                                    Log.i("XUPK","package name:"+configs[0]);
                                    xupkThreadClasses(configs[0],null);
                                }
                                else if(configs.length==2)
                                {
                                    Log.i("XUPK","package name:"+configs[0]);
                                    Log.i("XUPK","method info name:"+configs[1]);
                                    xupkThreadClasses(configs[0],configs[1]);
                                }          
                                else
                                {
                                    Log.e("XUPK", "Invalid configuration file:"+configPath);
                                    continue;
                                }                
                                
                                Log.e("XUPK", "Xupk run over");
                                long endMillis = System.currentTimeMillis();
                                String strTime=formatTime(endMillis-startMillis);
                                Log.e("XUPK","Time "+strTime);
                                // File file = new File(configPath);
                                // if(file.exists() && file.isFile())
                                // {
                                //     // 删除配置文件
                                //     if(!file.delete())
                                //     {
                                //         Log.e("XUPK", "File:"+configPath+" delete failed");
                                        
                                //     }
                                // }
                                Log.e("XUPK", "Programe will kill the xupk thread");
                                thread=null;
                                break;
                            }
                        }  
                        catch(Exception e)
                        {
                            e.printStackTrace();

                        }                
                    }  
                    thread=null;
                }
            });
            try
            {
                unpackAppMethod.invoke(null);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            thread.start();
        }
    }

    

    /**
     * 启动主动调用线程
     * ! 未使用
     */
    public void unpackWithClassLoader(int second)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Log.e("XUPK", "start sleep for "+second+" seconds)......");
                    Thread.sleep(second* 1000);
                    Log.e("XUPK", "sleep over and start xupkThread");
                    xupkThread();
                    Log.e("XUPK", "xupk run over");
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }             
            }
        }).start();
    }
    
    private boolean xupkThreadClasses(String packageName,String dexClassFileName)
    {    
        List<FileInfo> fileList=new ArrayList<>();
        try
        {   
            //Map<MethodInfo,Object> methodMap=new HashMap<>();
            List<String> dexClassPaths=new ArrayList<>();   
            String folder="data/data/"+packageName+"/xupk/";        
            if(dexClassFileName==null)
            {
                File file=new File(folder);
                File[] files = file.listFiles();
                if(files==null)
                {
                    return false;
                }
                for(File f:files)
                {
                    if(f.getName().contains("_class.json"))
                    {
                        String fileName=f.getAbsolutePath();
                        //String fileName=folder+file.getName();
                        dexClassPaths.add(fileName);
                    }
                }
            }
            else
            {
                String fileName=folder+dexClassFileName;
                dexClassPaths.add(fileName);
            }

            Method method_mapToFile = getMethod(getClassLoader(),"android.app.Xupk","mapToFile");
            Log.i("XUPK","Found "+dexClassPaths.size()+" _class.json files");
            for (int i = 0; i <dexClassPaths.size() ; i++)
            {
                String dexClassPath=dexClassPaths.get(i);
                
                // 添加数据
                FileInfo fileInfo=new FileInfo();
                fileInfo.fileName=dexClassPath;
                fileList.add(fileInfo);

                //Log.i("XUPK","dex class path:"+dexClassPath);

                File classesFile = new File(dexClassPath);
                String strDexClass = readFileString(dexClassPath);
                if(strDexClass==null)
                {
                    continue;
                }
                JSONObject jsonDexClass = new JSONObject(strDexClass);
                if(jsonDexClass.has("count"))
                {
                    //int count = jsonDexClass.getInt("count");
                    //Log.i("XUPK","load classes file:"+dexClassPath+",count:"+count);
                }

                JSONArray data = jsonDexClass.getJSONArray("data");  
                //Log.i("XUPK","Load file["+(i+1)+"/"+dexClassPaths.size()+"]:"+classesFile.getName()+",count of class:"+data.length()); 

                for (int j = 0; j < data.length(); j++)
                {
                    try 
                    {
                        // 去掉开头L和结尾分号,并将/换成.
                        // Lcom/fighter/sdk/report/abtest/ABTestConfig$1;   =>   com.fighter.sdk.report.abtest.ABTestConfig$1
                        String className =data.getString(j).substring(1,data.getString(j).length()-1).replace("/",".");
                        if(className.equals("android.app.Xupk"))
                        {
                            continue;
                        }
                        ClassInfo classInfo=new ClassInfo();
                        classInfo.className=className;
                        fileInfo.classList.add(classInfo);

                        Class klass=getClassLoader().loadClass(className);
                        int count=loadAllMethodsWithClass(klass,classInfo);

                        Log.i("XUPK","Load file["+(i+1)+"/"+dexClassPaths.size()+"]:"+classesFile.getName()+",class["+(j+1)+"/"+data.length()+"]:"+className+",method count:"+count); 
                    } 
                    catch (Error | Exception e) 
                    {
                        e.printStackTrace();
                        continue;
                    }              
                }              
            }

            HashSet<String> methodBlackList = new HashSet();
            String methodTmpFileName = folder + "xk_last_method.tmp";
            String blackListMethodFileName = folder + "xk_blacklist.config";
            JSONObject blackListJson = null;
            try {
                String methodTemp = readFileString(methodTmpFileName);
                FileWriter writer = new FileWriter(new File(blackListMethodFileName), true);
                if(methodTemp != null && methodTemp != "")
                {
                    writer.write(methodTemp);
                    writer.write("\n");
                    writer.close();
                }
                String blStr = readFileString(blackListMethodFileName);
                for (String s :blStr.split("\n")) {
                    methodBlackList.add(s);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 遍历方法map,并进行主动调用       
            for(int x=0;x<fileList.size();x++)
            {
                String fileName=fileList.get(x).fileName;
                List<ClassInfo> classList=fileList.get(x).classList;

                for(int y=0;y<classList.size();y++)
                {
                    String className=classList.get(y).className;
                    Map<String,Object> methodMap=classList.get(y).methodMap;

                    String log="File["+(x+1)+"/"+fileList.size()+"]:"+fileName+",";
                    log+="Class["+(y+1)+"/"+classList.size()+"]:"+className+",";
                    Log.i("XUPK",log);

                    for(Map.Entry<String,Object> entry :methodMap.entrySet())
                    {
                        try
                        {
                            String methodName=entry.getKey();
                            Object method=entry.getValue();
                            String cm = className + ":" + methodName;
                            cm = cm.replaceAll("\\s", "%20");
                            if(methodBlackList.contains(cm))
                            {
                                continue;
                            }
                            writeFileString(methodTmpFileName, cm);
                            fakeInvoke(method);
                        }
                        catch(Error | Exception e)
                        {
                            // e.printStackTrace();
                            Log.i("XUPK", "expection " + className + ":" + entry.getKey());
                            continue;
                        }              
                    }
                }
                method_mapToFile.invoke(null); 
            }
            File methodTmpFile = new File(methodTmpFileName);
            if(methodTmpFile.exists()) {
                methodTmpFile.delete();
            }
            return true;             
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.i("XUPK","xupkThreadClasses error");
        }
        return false;
    }

    /**
     * 读取配置文件
     */
    public static @Nullable String readFileString( @Nullable String fileName)
    {
        File file = new File(fileName);
        StringBuilder content = new StringBuilder();

        try (FileInputStream inputStream = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if(content.length() > 0) {
                    content.append("\n");
                }
                content.append(line);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return content.toString();
    }

    public static void writeFileString(@Nullable String filename, @Nullable String content)
    {
        File file = new File(filename);
        try (FileOutputStream outputStream = new FileOutputStream(file);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            writer.write(content);
            writer.flush();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static native void native_fakeInvoke(Object method);
    private static native void mapToFile();
    private static native void unpackAppFlag();
}