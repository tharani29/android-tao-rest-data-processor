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
package ua.at.tsvetkov.data_processor.requests;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import ua.at.tsvetkov.data_processor.processors.Processor;

/**
 * The main class for the file request building. If not specified the request be built with basic configuration parameters specified in
 * {@link ua.at.tsvetkov.data_processor.DataProcessorConfiguration DataProcessorConfiguration}.
 * 
 * @author lordtao
 */
public class FileRequest extends Request {

   private FileInputStream inputStream;

   public FileRequest() {

   }

   /**
    * Return new instance of FileRequest.
    * 
    * @return
    */
   public static FileRequest newInstance() {
      return new FileRequest();
   }

   /**
    * Starts the request and returns a response data as InputStream
    * 
    * @return
    * @throws IOException
    */
   public InputStream getInputStream() throws IOException {
      inputStream = new FileInputStream(new File(toString()));
      statusCode = Processor.FILE_SUCCESS;
      return inputStream;
   }

   /**
    * Set path
    * 
    * @param url
    */
   public FileRequest setPath(String path) {
      this.path = path;
      return this;
   }

   /**
    * Set full path
    * 
    * @param url
    */
   public FileRequest setFullPath(String path) {
      this.url = path;
      return this;
   }

   @Override
   public Request build() {
      scheme = "";
      return super.build();
   }

   /**
    * Release resources associated with this request. You must call this, or significant resources (sockets and memory) may be leaked.
    */
   @Override
   public void close() throws Exception {
      inputStream.close();
   }

}
