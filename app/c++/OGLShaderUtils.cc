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

#include <iosfwd>
#include <iostream>
#include <fstream>
#include <sstream>
#include <memory>

#include <GLES/gl.h>
#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <EGL/egl.h>

#include "OGLShaderUtils.h"

typedef struct token_string
{
   GLuint code;
   const char *description;
} token_string_t;

static const token_string_t OGL_ERRORS[] = {
      { GL_NO_ERROR, "no error" },
      { GL_INVALID_ENUM, "invalid enumerant" },
      { GL_INVALID_VALUE, "invalid value" },
      { GL_INVALID_OPERATION, "invalid operation" },
      { GL_STACK_OVERFLOW, "stack overflow" },
      { GL_STACK_UNDERFLOW, "stack underflow" },
      { GL_OUT_OF_MEMORY, "out of memory" },
//      { GL_TABLE_TOO_LARGE, "table too large" },
//      { GL_INVALID_FRAMEBUFFER_OPERATION_EXT, "invalid framebuffer operation" },
      { ~0u, NULL } /* end of list indicator */
};

bool isGLOk(GLenum& err, std::stringstream *strst)
//-------------------------------------------------
{
   err = glGetError();
   bool ret = (err == GL_NO_ERROR);
   GLenum err2 = err;
   while (err2 != GL_NO_ERROR)
   {
      if (strst != nullptr)
      {
         for (int i = 0; OGL_ERRORS[i].description; i++)
         {
            if (OGL_ERRORS[i].code == err2)
               *strst << (const GLubyte *) OGL_ERRORS[i].description << std::endl;;
         }
      }
      err2 = glGetError();
      if ( (strst != nullptr) && (err2 != GL_NO_ERROR) )
         *strst << std::endl;
   }
   return ret;
}

GLuint compile(const std::string& source, GLenum type, GLenum& err, std::stringstream *errbuf)
//------------------------------------------------------------------------------------------
{
   GLuint handle = glCreateShader(type);
   char *p = const_cast<char *>(source.c_str());
   glShaderSource(handle, 1, (const GLchar **) &p, nullptr);
   if (! isGLOk(err, errbuf))
   {
      if (errbuf != nullptr)
         *errbuf << " (setting shader source)";
      return 0;
   }
   glCompileShader(handle);
   // if (! isGLOk(errbuf))
   //    return 0;
   bool compiled = isGLOk(err, errbuf);
   GLint status[1];
   glGetShaderiv(handle, GL_COMPILE_STATUS, &status[0]);
   if ( (status[0] == GL_FALSE) || (! compiled) )
   {
      if (errbuf != nullptr)
         *errbuf << "Compile error: " << std::endl << source.c_str();
      status[0] = 0;
      glGetShaderiv(handle, GL_INFO_LOG_LENGTH, &status[0]);
      std::unique_ptr<GLchar> logoutput(new GLchar[status[0]]);
      GLsizei loglen[1];
      glGetShaderInfoLog(handle, status[0], &loglen[0], logoutput.get());
      if (errbuf != nullptr)
         *errbuf << std::string(logoutput.get());
      return 0;
   }
   else
   {
      status[0] = 0;
      glGetShaderiv(handle, GL_INFO_LOG_LENGTH, &status[0]);
      std::unique_ptr<GLchar> logoutput(new GLchar[status[0] + 1]);
      GLsizei loglen[1];
      glGetShaderInfoLog(handle, status[0], &loglen[0], logoutput.get());
      std::string logout = std::string(logoutput.get());
      if (logout.length() > 0)
         std::cout << "Compile Shader OK:" << std::endl << logout;
   }
   return handle;
}

bool link(const GLuint program, GLenum& err, std::stringstream* errbuf)
//-----------------------------------------------------------------------------
{
   glLinkProgram(program);
   if (! isGLOk(err, errbuf))
   {
      if (errbuf != nullptr)
         *errbuf << " (Error linking shaders to shader program)";
   }
   GLint status[1];
   glGetProgramiv(program, GL_LINK_STATUS, &status[0]);
   if (status[0] == GL_FALSE)
   {
      if (errbuf != nullptr)
         *errbuf << "Link error: ";
      status[0] = 0;
      glGetProgramiv(program, GL_INFO_LOG_LENGTH, &status[0]);
      std::unique_ptr<GLchar> logoutput(new GLchar[status[0]]);
      GLsizei loglen[1];
      glGetProgramInfoLog(program, status[0], &loglen[0], logoutput.get());
      if (errbuf != nullptr)
         *errbuf << std::string(logoutput.get());
      return false;
   }
   else
   {
      status[0] = 0;
      glGetProgramiv(program, GL_INFO_LOG_LENGTH, &status[0]);
      std::unique_ptr<GLchar> logoutput(new GLchar[status[0] + 1]);
      GLsizei loglen[1];
      glGetProgramInfoLog(program, status[0], &loglen[0], logoutput.get());
      std::string logout = logoutput.get();
      if ( (logout.length() > 0) && (errbuf != nullptr) )
         *errbuf << "Shader Link Status: " << logout.c_str();
   }
   return true;
}

GLuint compile_link(const std::string& vertex_source, const std::string fragment_source,
                    GLuint &vertexShader, GLuint &fragmentShader,
                    GLenum& err, std::stringstream *errbuf)
//---------------------------------------------------------------------------------------
{
   vertexShader = fragmentShader = 0;
   vertexShader = compile(vertex_source, GL_VERTEX_SHADER, err, errbuf);
   if (vertexShader == 0)
      return 0;

   fragmentShader = compile(fragment_source, GL_FRAGMENT_SHADER, err, errbuf);
   if (fragmentShader == 0)
      return 0;

   clearGLErrors();
   GLuint program = glCreateProgram();
   if (program == 0)
   {
      if (errbuf != nullptr)
         *errbuf << "Error creating shader program.";
      return 0;
   }
   glAttachShader(program, vertexShader);
   glAttachShader(program, fragmentShader);
   if (! isGLOk(err, errbuf))
   {
      if (errbuf != nullptr)
         *errbuf << "Error attaching shaders to shader program.";
      return 0;
   }
   if (! link(program, err, errbuf))
      return 0;
   return program;
}
