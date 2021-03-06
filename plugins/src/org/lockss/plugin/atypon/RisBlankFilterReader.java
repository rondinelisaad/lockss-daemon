/*
 * $Id$
 */

/*

Copyright (c) 2000-2018 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.atypon;

import java.io.*;
import java.util.regex.Matcher;
import org.apache.commons.lang.StringUtils;
import org.lockss.filter.*;
import org.lockss.util.*;

/**
 * This filter will use an updated RisFilterReader
 * but until we respin the daemon, need to remove blank lines with this version
 */
public class RisBlankFilterReader extends RisFilterReader {
  private static final Logger log = Logger.getLogger(RisBlankFilterReader.class);

  public RisBlankFilterReader(InputStream inputStream, String encoding, String... tags)
			throws UnsupportedEncodingException {
		super(inputStream, encoding, tags);
	}
  
  @Override
  public String rewriteLine(String line) {
	  // filter out blank lines as well - unimportant for hashed comparison
	if (StringUtils.isBlank(line)) {
		return null;
	}
    Matcher mat = tagPattern.matcher(line);
    if (mat.find()) {
      String tag = getTag(mat);
      removingTag = tagSet.contains(tag);
    }
    return removingTag ? null : line;
  
  }
}
