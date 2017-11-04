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

#ifndef _OGLSHADERUTILS_H
#define _OGLSHADERUTILS_H

#include <string>

#include <GLES3/gl3.h>
#include <EGL/egl.h>


inline void clearGLErrors() { while (glGetError() != GL_NO_ERROR); }

bool isGLOk(GLenum& err, std::stringstream *strst =nullptr);

GLuint compile(const std::string& source, GLenum type, GLenum& err, std::stringstream *errbuf);

bool link(const GLuint program, GLenum& err, std::stringstream* errbuf =nullptr);

GLuint compile_link(const std::string& vertex_source, const std::string fragment_source,
                    GLuint &vertexShader, GLuint &fragmentShader,
                    GLenum& err, std::stringstream *errbuf =nullptr);

#endif //_OGLSHADERUTILS_H
