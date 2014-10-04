// Copyright (C) 2013-2014, Gabriel Dos Reis.
// All rights reserved.
// Written by Gabriel Dos Reis.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     - Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//
//     - Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in
//       the documentation and/or other materials provided with the
//       distribution.
//
//     - Neither the name of OpenAxiom. nor the names of its contributors
//       may be used to endorse or promote products derived from this
//       software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
// IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
// OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
// EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#include "server.h"

namespace OpenAxiom {
   static Command
   process_arguments(int argc, char* argv[]) {
      Command cmd;
      preprocess_arguments(&cmd, argc, argv);
      return cmd;
   }
   
   Server::Server(int argc, char* argv[])
         : cmd(process_arguments(argc, argv)),
           fs(cmd.root_dir),
           interp_db(fs.dbdir() + "/interp.daase")
   { }

   Server::~Server() {
      if (state() == QProcess::Running)
         terminate();
   }

   void
   Server::input(const QString& s) {
      write(s.toLatin1());
      write("\n");
   }

   void
   Server::launch() {
      QStringList args;
      for (auto arg : cmd.rt_args)
         args << arg;
      args << "--" << "--role=server";
      for (int i = 1; i < cmd.core.argc; ++i)
         args << cmd.core.argv[i];
      start(make_path_for(cmd.root_dir, Driver::core), args);
   }
}
