/*******************************************************************************
 * Copyright (c) 2014 Alexandr Tsvetkov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * Contributors:
 *     Alexandr Tsvetkov - initial API and implementation
 *
 * Project:
 *     TAO Data Processor
 *
 * License agreement:
 *
 * 1. This code is published AS IS. Author is not responsible for any damage that can be
 *    caused by any application that uses this code.
 * 2. Author does not give a garantee, that this code is error free.
 * 3. This code can be used in NON-COMMERCIAL applications AS IS without any special
 *    permission from author.
 * 4. This code can be modified without any special permission from author IF AND ONLY IF
 *    this license agreement will remain unchanged.
 ******************************************************************************/
package ua.at.tsvetkov.data_processor.processors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.security.InvalidParameterException;

import org.apache.http.HttpStatus;

import ua.at.tsvetkov.data_processor.DataProcessor;
import ua.at.tsvetkov.data_processor.interfaces.InputStreamDataInterface;
import ua.at.tsvetkov.data_processor.interfaces.StringDataInterface;
import ua.at.tsvetkov.data_processor.processors.abstractclasses.AbstractProcessor;
import ua.at.tsvetkov.data_processor.requests.Request;
import ua.at.tsvetkov.data_processor.threads.DataProcessingThreadFactory;
import ua.at.tsvetkov.data_processor.threads.DataProcessorThreadPool;
import ua.at.tsvetkov.util.Const;
import ua.at.tsvetkov.util.Log;
import android.os.Handler;
import android.os.Looper;

public class Processor<T> {

   private static final String  FILE_EXIST              = "File exist: ";
   private static final String  CAN_T_CREATE            = "Can't create ";
   private static final String  LOADING_TIME            = "Loading time = ";
   private static final String  END_STRING              = " ]";
   private static final String  MS                      = " ms. [ ";

   private static final String  INVALID_CLASS_PARAMETER = "Invalid class parameter. A class for data processing must implement InputStreamDataInterface or StringDataInterface either extend AbstractProcessor";
   private static final String  INVALID_PARAMETER       = "Invalid parameter. Request or Class can't be eq null";
   public static final int      BUFFER                  = 8 * Const.KB;
   /**
    * File was opened success.
    */
   public static final int      FILE_SUCCESS            = 0;
   /**
    * Processing ERROR code.
    */
   public static final int      ERROR                   = -1;

   private Request              request;
   private AbstractProcessor<T> processor;
   private InputStream          inputStream;
   private String               cacheFileName;
   private Class<T>             clazz;
   private Callback<T>          callback;
   private Thread               thread;
   private final Handler        handler;
   private DataProcessor        dataProcessor;
   private T                    result;
   private boolean              isFinished              = false;
   private int                  statusCode;

   /**
    * @param dataProcessor
    * @param request
    * @param clazz
    */
   public Processor(DataProcessor dataProcessor, Request request, Class<T> clazz) {
      if (request == null || clazz == null) {
         throw new InvalidParameterException(INVALID_PARAMETER);
      }
      this.dataProcessor = dataProcessor;
      this.request = request;
      this.clazz = clazz;
      this.callback = null;

      if (Looper.myLooper() != null) {
         handler = new Handler();
      } else {
         handler = null;
      }
      thread = Thread.currentThread();
   }

   /**
    * @param dataProcessor
    * @param request
    * @param clazz
    * @param callback
    */
   public Processor(DataProcessor dataProcessor, Request request, Class<T> clazz, Callback<T> callback) {
      if (request == null || clazz == null) {
         throw new InvalidParameterException(INVALID_PARAMETER);
      }
      this.dataProcessor = dataProcessor;
      this.request = request;
      this.clazz = clazz;
      this.callback = callback;

      if (Looper.myLooper() != null) {
         handler = new Handler();
      } else {
         handler = null;
      }
      thread = Thread.currentThread();
   }

   public T execute() {
      if (!isCorrectClass()) {
         throw new InvalidParameterException(INVALID_CLASS_PARAMETER);
      }
      cacheFileName = request.getCacheFileName();
      try {
         if (cacheFileName != null && cacheFileName.length() > 0) {
            saveToFile();
         } else {
            inputStream = request.getInputStream();
         }
         if (request.getStatusCode() == FILE_SUCCESS || request.getStatusCode() == HttpStatus.SC_OK) {
            createProcessor();
         }
         if (processor != null) {
            processor.parse(inputStream);
            sendMessage(request.getStatusCode(), processor.getResult());
         } else {
            sendMessage(request.getStatusCode(), null);
            // sendMessage(request.getStatusCode(), inputStream); WTF???
         }
      } catch (Exception e) {
         Log.e("Error in request: " + request.toString(), e);
         sendMessage(ERROR, null);
      } finally {
         try {
            if (inputStream != null) {
               inputStream.close();
            }
            request.close();
         } catch (Exception e) {
            Log.e(e);
         }
      }
      if (DataProcessor.getInstance().getConfiguration().isLogEnabled() && DataProcessor.getInstance().getConfiguration().isShowProcessingTime()) {
         long time = System.currentTimeMillis() - request.getStartTime();
         Log.v(LOADING_TIME + time + MS + request + END_STRING);
      }
      if (processor != null) {
         return processor.getResult();
      } else {
         return null;
      }
   }

   /**
	 * 
	 */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   private void createProcessor() {
      try {
         if (AbstractProcessor.class.isAssignableFrom(clazz)) {
            Constructor[] construct = clazz.getDeclaredConstructors();
            processor = (AbstractProcessor) construct[0].newInstance();
         } else if (InputStreamDataInterface.class.isAssignableFrom(clazz)) {
            Constructor[] construct = clazz.getDeclaredConstructors();
            processor = new InputStreamProcessor((InputStreamDataInterface) construct[0].newInstance());
         } else if (StringDataInterface.class.isAssignableFrom(clazz)) {
            Constructor[] construct = clazz.getDeclaredConstructors();
            processor = new StringProcessor((StringDataInterface) construct[0].newInstance());
         } else {
            Log.e(INVALID_CLASS_PARAMETER);
         }
      } catch (Exception e) {
         Log.e(CAN_T_CREATE + clazz.getCanonicalName(), e);
      }
   }

   /**
    * @return
    */
   private boolean isCorrectClass() {
      if (InputStreamDataInterface.class.isAssignableFrom(clazz)) {
         return true;
      } else if (StringDataInterface.class.isAssignableFrom(clazz)) {
         return true;
      } else if (AbstractProcessor.class.isAssignableFrom(clazz)) {
         return true;
      } else {
         return false;
      }
   }

   private void sendMessage(final int statusCode, final T object) {
      setResult(object);
      setStatus(statusCode);
      if (callback != null) {
         if (thread == Thread.currentThread()) {
            callback.onFinish(object, statusCode);
         } else {
            handler.post(new Runnable() {

               @Override
               public void run() {
                  callback.onFinish(object, statusCode);
               }
            });
         }
      }
   }

   public void executeAsync() {
      Runnable runnable = new Runnable() {

         @Override
         public void run() {
            execute();
         }

      };
      DataProcessorThreadPool pool = dataProcessor.getThreadPool();
      if (pool != null) {
         pool.execute(runnable);
      } else {
         new Thread(runnable, DataProcessingThreadFactory.THREAD_NAME).start();
      }
   }

   private void saveToFile() throws IOException {
      File f = new File(cacheFileName);
      if (!request.isNeedToRewriteFile() && f.exists() && f.length() > 0) {
         if (DataProcessor.getInstance().getConfiguration().isLogEnabled()) {
            Log.w(FILE_EXIST + cacheFileName);
         }
         return;
      }
      FileOutputStream out = new FileOutputStream(cacheFileName);
      byte[] buffer = new byte[BUFFER];
      int bytesRead = -1;
      inputStream = request.getInputStream();
      while ((bytesRead = inputStream.read(buffer)) != -1) {
         out.write(buffer, 0, bytesRead);
      }
      out.flush();
      out.close();
      inputStream.close();
      inputStream = new FileInputStream(cacheFileName);
   }

   public void setCallback(Callback<T> callback) {
      this.callback = callback;
   }

   /**
    * Return resulting object
    * 
    * @return
    */
   public T getResult() {
      return result;
   }

   /**
    * @param statusCode
    * @return
    */
   public int getStatus() {
      return statusCode;
   }

   /**
    * Return Data class
    * 
    * @return
    */
   public Class<?> getDataClass() {
      return clazz.getClass();
   }

   /**
    * @return
    */
   public boolean isFinished() {
      return isFinished;
   }

   /**
    * Redelivery result in callback
    */
   public void redelivery() {
      sendMessage(statusCode, result);
   }

   private T setResult(T result) {
      isFinished = true;
      return this.result = result;
   }

   /**
    * @param statusCode
    */
   private void setStatus(int statusCode) {
      this.statusCode = statusCode;
   }

   public static interface Callback<T> {

      /**
       * Runs on the UI thread.
       * 
       * @param obj
       * @param what
       */
      public abstract void onFinish(T obj, int what);

   }

}
