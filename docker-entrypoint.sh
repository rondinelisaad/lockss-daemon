#!/bin/bash

# Validating if the directory is empty
if [ ! -f /usr/share/lockss-daemon/build.xml ]; then
   cd /usr/share
   git clone https://github.com/rondinelisaad/lockss-daemon
fi

#setlogcons
#eval $(locale | sed -e 's/\(.*\)=.*/export \1=en_US.UTF-8/')
msginit --no-translator -i /usr/share/lockss-daemon/src/i18n/keys.pot -o /tmp/default7256016110946819070.po

exec /bin/bash
