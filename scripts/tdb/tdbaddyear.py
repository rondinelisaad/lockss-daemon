#!/usr/bin/env python2

'''A rudimentary script to add additional years of serial publications in TDB
files.'''

__copyright__ = '''\
Copyright (c) 2000-2017, Board of Trustees of Leland Stanford Jr. University
All rights reserved.'''

__license__ = '''\
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
may be used to endorse or promote products derived from this software without
specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.'''

__version__ = '0.1.0'

import cStringIO
import optparse
import re
import subprocess

class _TdbAddYearOptions(object):

    @staticmethod
    def make_parser():
        usage = '%prog [OPTIONS] FILE...'
        parser = optparse.OptionParser(version=__version__, usage=usage, description=__doc__)
        parser.add_option('--copyright', '-C', action='store_true', help='show copyright and exit')
        parser.add_option('--license', '-L', action='store_true', help='show license and exit')
        return parser

    def __init__(self, parser, opts, args):
        super(_TdbAddYearOptions, self).__init__()
        # --copyright, --license, (--help, --version already done)
        if any([opts.copyright, opts.license]):
            if opts.copyright: print copyright__
            elif opts.license: print __license__
            else: raise RuntimeError, 'internal error'
            sys.exit()
        # files
        if len(args) == 0: parser.error('at least one file is required')
        self.files = args[:]

class _Au(object):
  '''An internal class to represent an AU entry.'''

  def __init__(self, implicitmap, body, changed=False):
    '''
    Constructor.
    Parameters (all becoming fields, except 'body' which is split into the
    'values' array and can be regenerated with 'generate_body()'):
    - implicitmap (dict): the AU entry's implicit<...> specification, mapping
    from string (key in the implicit<...> statement) to integer (zero-based
    index of the key in the implicit<...> statement)
    - body (string): the AU entry's textual body
    - changed (boolean): a "dirty bit" set to True to indicate the entry has
    been modified (default: False)
    '''
    super(_Au, self).__init__()
    # implicitmap/changed
    self.implicitmap = implicitmap
    self.changed = changed
    # values
    vals = body.split(';')
    namelen = len(vals) - len(self.implicitmap) + 1
    if namelen == 1:
      self.values = vals
    else:
      namebegin = self.implicitmap['name']
      nameend = namebegin + namelen
      self.values = vals[:namebegin] + [';'.join(vals[namebegin:nameend])] + vals[nameend:]

  def is_changed(self):
    '''Determines if the AU's "dirty bit" is set.'''
    return self.changed

  def get_name(self):
    '''Returns the AU's name.'''
    return self._get('name')

  def get_proxy(self):
    '''Returns the AU's proxy setting (or None if unset).'''
    val = self._get('hidden[proxy]')
    if val is not None and len(val) == 0: val = None
    return val

  def set_proxy(self, val):
    '''Sets the AU's proxy setting to the given value (or None to unset).'''
    if val is None: val = ''
    self._set('hidden[proxy]', val)

  def get_status(self):
    '''Returns the AU's status.'''
    return self._get('status')

  def set_status(self, val):
    '''Sets the AUs'a status to the given value.'''
    self._set('status', val)

  def get_status2(self):
    '''Returns the AU's status2.'''
    return self._get('status2')

  def set_status2(self, val):
    '''Sets the AU's status2 to the given value.'''
    self._set('status2', val)

  def generate_body(self):
    '''Regenerates the AU's textual body.'''
    return ';'.join(self.values)

  def _get(self, field):
    '''
    Retrieves the value of the given field with leading and trailing whitespace
    stripped (or None if no such field is defined).
    '''
    fieldindex = self.implicitmap.get(field)
    if fieldindex is None: return None
    else: return self.values[fieldindex].strip()

  def _set(self, field, val):
    '''
    Sets the given field to the given value with added leading and trailing
    whitespace, and sets the "dirty bit" if the new value is different from the
    old value. Raises KeyError if no such field is defined.
    '''
    fieldindex = self.implicitmap.get(field)
    if fieldindex is None:
      raise KeyError, '%s' % (field,)
    if val.strip() != self.values[fieldindex].strip():
      self.values[fieldindex] = ' %s ' % (val,)
      self.changed = True

_AUID = 0
_FILE = 1
_LINE = 2
_TITLE = 3
_NAME = 4
_YEAR = 5
_VOLUME = 6
_PYEAR = 7
_PVOLUME = 8
_PVOLNAME = 9

def _tdbout(options):
    tdbout = 'scripts/tdb/tdbout'
    cmd = [tdbout, '--tsv=auid,file,line,title,name,year,volume,param[year],param[volume],param[volume_name]']
    cmd.extend(options.files)
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    (out, err) = proc.communicate() # out and err are (potentially huge) strings
    if proc.returncode != 0:
        sys.exit('%s exited with error code %d: %s' % (tdbout, proc.returncode, cStringIO.StringIO(err)))
    ret = [line.strip().split('\t') for line in cStringIO.StringIO(out)]
    for auentry in ret: auentry[_LINE] = int(auentry[_LINE])
    return ret

# A regular expression to match implicit<...> lines
# - Group 1: semicolon-separated body of the implicit<...> statement
_IMPLICIT = re.compile(r'^[ \t]*implicit[ \t]*<(.*)>[ \t]*(#.*)?$')

# A regular expression to match au<...> lines
# - Group 1: semicolon-separated body of the au<...> statement
_AU = re.compile(r'^[ \t]*au[ \t]*<(.*)>[ \t]*(#.*)?$')

def _recognize(options, aus):
    aindex = 0
    errors = 0
    for fstr in options.files:
        continue_outer = False
        with open(fstr, 'r') as f:
            for lineindex, line in enumerate(f):
                mat = _IMPLICIT.search(line)
                if mat is not None:
                    impl = [trait.strip() for trait in mat.group(1).split(';')]
                    implmap = dict([(x, i) for i, x in enumerate(impl)])
                    if 'name' not in implmap:
                        errors = errors + 1
                        sys.stderr.write('%s:%s: implicit statement does not specify \'name\'\n' % (fstr, lineindex + 1))
                        continue_outer = True # next file
                        break
                    if 'status' not in implmap:
                        errors = errors + 1
                        sys.stderr.write('%s:%s: implicit statement does not specify \'status\'\n' % (fstr, lineindex + 1))
                        continue_outer = True # next file
                        break
                    continue # next line
                if aindex < len(aus) and fstr == aus[aindex][1] and lineindex + 1 == aus[aindex][2]:
                    mat = _AU.search(line)
                    if mat is None:
                        errors = errors + 1
                        sys.stderr.write('%s:%s: text recognizer does not match definition for %s\n' % (fstr, lineindex + 1, aus[aindex][0]))
                        continue_outer = True # next file
                        break
                    au = _Au(implmap, mat.group(1))
                    aus[aindex].append(au)
                    aindex = aindex + 1
                    continue # next line
            if continue_outer: continue # next file
    if len(aus) != aindex:
        errors = errors + 1
        sys.stderr.write('error: tdbout parsed %d AU declarations but tdbedit found %d\n' % (len(aus), aindex))
    if errors > 0:
        sys.exit('%d %s; exiting' % (errors, 'error' if errors == 1 else 'errors'))

def _find_endpoints(options, aus):
    ret = list()
    aindex = 0
    while aindex < len(aus):
        auentry = aus[aindex]
        if aindex == len(aus) - 1 or auentry[_TITLE] != aus[aindex+1][_TITLE]:
            ret.append(aindex)
        aindex = aindex + 1
    return ret

def _main():
    # Parse command line
    parser = _TdbAddYearOptions.make_parser()
    (opts, args) = parser.parse_args()
    options = _TdbAddYearOptions(parser, opts, args)
    aus = _tdbout(options)
    # Get AUIDs and scan files for AU entries
    aus = _tdbout(options)
    _recognize(options, aus)
    # Find candidates
    endpoints = _find_endpoints(options, aus)
    for aindex in endpoints:
        auentry = aus[aindex]
        print '%s:%d: %s' % (auentry[_FILE], auentry[_LINE], auentry[_NAME]) ###DEBUG

# Main entry point
if __name__ == '__main__': _main()

