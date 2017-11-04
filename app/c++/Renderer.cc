/*
Copyright (c) 2017 Donald Munro

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

#include <iostream>
#include <iomanip>
#include <limits>
#include <thread>

#include <GLES/gl.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>
#include <EGL/egl.h>
#include <tango_client_api.h>

#include "glm/gtc/matrix_transform.hpp"
#include "glm/gtc/type_ptr.hpp"
#include "glm/gtx/string_cast.hpp"

#include "OGLShaderUtils.h"
#include "Renderer.h"
#ifndef ANDROID_LOG_TAG
#define ANDROID_LOG_TAG "Renderer"
#endif
#include "android-logging.h"

#define GL_TEXTURE_EXTERNAL_OES           0x8D65

OGLRenderer::OGLRenderer(TangoCameraId cameraId, std::atomic_bool& connected_) : Renderer(cameraId, connected_)
//--------------------------------------------------------------------------------------------------------------
{
   vertex_coordinates.reset(new GLfloat[8]
         {
               1,1,
               0,1,
               1,0,
               0,0
         });
   texture_faces.reset(new GLshort[6] { 2,3,1, 0,2,1 });
}

OGLRenderer::~OGLRenderer()
//-------------------
{
   clearGLErrors();
}


bool OGLRenderer::init(std::stringstream& errs)
//---------------------------------------------------------------------------------------------------------------------
{
   for (int i=0; i<10; i++)
   {
      try { context = eglGetCurrentContext(); } catch (const std::exception& e) { context = nullptr; ALOGE("%s\n", e.what()); }
      if (context != nullptr)
         break;
      std::this_thread::sleep_for(std::chrono::milliseconds(20));
   }
   if (context == nullptr)
      throw std::runtime_error("Invalid OpenGL context");
   GLenum err;
   std::stringstream errbuf;
   shader_program = compile_link(vertex_shader_src, fragment_shader_src, vertexShader, fragmentShader, err, &errbuf);
   if (shader_program == 0)
   {
      ALOGE("Error compiling/linking shaders (%s)", errbuf.str().c_str());
      errs << "Error compiling/linking shaders (" << errbuf.str() << ")";
      return false;
   }
   position_attrib = glGetAttribLocation(shader_program, "vPosition");
   if (position_attrib < 0)
   {
      errs << "Could not bind vertex attribute 'vPosition'" << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }
   texture_attrib = glGetAttribLocation(shader_program, "tPosition");
   if (texture_attrib < 0)
   {
      errs << "Could not bind texture attribute 'tPosition'" << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }
   MVP_uniform = glGetUniformLocation(shader_program, "MVP");
   std::stringstream errs2;
   if ( (! isGLOk(err, &errs2)) || (MVP_uniform < 0) )
   {
      errs << "Error: Initializing Model-View-Projection Matrix uniform location: " << errs2.str().c_str() << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }
   textureSamplerUniform = glGetUniformLocation(shader_program, "previewSampler");
   if ( (! isGLOk(err, &errs2)) || (textureSamplerUniform < 0) )
   {
      errs << "Error: Initializing texture sampler uniform location: " << errs2.str().c_str() << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }

   glUseProgram(shader_program);
   if (! isGLOk(err, &errs))
   {
      errs << "Error: Initializing shader program: " << errs.str().c_str() << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }

   glGenTextures(1, &camera_texture);
   if (! isGLOk(err, &errs))
   {
      errs << "Error: Creating texture: " << errs.str().c_str() << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }
   glActiveTexture(GL_TEXTURE0);
   glBindTexture(GL_TEXTURE_EXTERNAL_OES, camera_texture);
   if (! isGLOk(err, &errs))
   {
      errs << "Error: Binding texture to GL_TEXTURE_EXTERNAL_OES: " << errs.str().c_str() << std::endl;
      ALOGE("%s", errs.str().c_str());
      return false;
   }
   glTexParameterf(GL_TEXTURE_EXTERNAL_OES,  GL_TEXTURE_MIN_FILTER, GL_NEAREST);
   glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
   glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
   glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

   glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

   ALOGD("Renderer::init OK");
//   timestamp = last_timestamp = std::chrono::high_resolution_clock::now();
   return true;
}

void OGLRenderer::resize(int width, int height)
//---------------------------------------------
{
   w = width;
   h = height;
   z = -1;
   glViewport(0, 0, width, height);
   glm::mat4 projection = glm::ortho<double>(0.0, static_cast<double>(w), 0.0, static_cast<double>(h), 0.2, 120.0);
   glm::mat4 view = glm::lookAt(glm::vec3(0, 0, 0), glm::vec3(0, 0, -1), glm::vec3(0, 1, 0));
   MVP = projection * view;
   vertices.reset(new GLfloat[12]
   {
        w, 0,  z, // bottom-right
        0, 0,  z, // bottom-left
        w, h,  z, // top-right
        0, h,  z, // top-left
   });

}

void OGLRenderer::render()
//---------------------
{
   GLenum err;
   std::stringstream errs, outs;

   clearGLErrors();
   glClearColor(0.0, 0.0, 0.0, 1.0);
   glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
   if ( (camera_texture > 0) && (vertices) && (connected) )// && (! taking_photo) )
   {
      glViewport(0, 0, imagewidth, imageheight);
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_EXTERNAL_OES, camera_texture);
      glUseProgram(shader_program);
      glVertexAttribPointer(position_attrib, 3, GL_FLOAT, GL_FALSE, 0,  vertices.get());
      glEnableVertexAttribArray(position_attrib);
      glVertexAttribPointer(texture_attrib, 2, GL_FLOAT, GL_FALSE, 0, vertex_coordinates.get());
      glEnableVertexAttribArray(texture_attrib);
      glUniformMatrix4fv(MVP_uniform, 1, false, glm::value_ptr(MVP));
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_EXTERNAL_OES, camera_texture);
      last_timestamp = timestamp;
      TangoErrorType ret = TangoService_updateTextureExternalOes(camera_id, camera_texture, &timestamp);
      if (ret == TANGO_SUCCESS)
         glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, texture_faces.get());
      else
         ALOGE("Renderer::render: ERROR in TangoService_updateTextureExternalOes (%d)", ret);
      glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
      glDisableVertexAttribArray(position_attrib);
      glDisableVertexAttribArray(texture_attrib);
      glUseProgram(0);
      if (! isGLOk(err, &errs))
      {
         errs << "Renderer::render: OpenGL Error %s" << errs.str().c_str() << std::endl;
         ALOGE("Renderer::render: OpenGL Error %s", errs.str().c_str());
      }
   }
}
