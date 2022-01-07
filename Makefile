install:
	cp out/artifacts/Ce_main_jar/Ce.main.jar /usr/local/bin/Ce0.jar
	#cp slf4j-nop-2.0.0-alpha1.jar /usr/local/bin
	cp ce.sh /usr/local/bin/ce0
	#cp freechains-host.sh         /usr/local/bin/freechains-host
	#cp freechains-sync.sh         /usr/local/bin/freechains-sync
	ls -l /usr/local/bin/[Cc]e*
	#freechains --version

ce1:
	gpp tst/ce1/ce1.ce > /tmp/ce1.ce
	ce /tmp/ce1.ce

tmp:
	gpp tst/tmp.ce > /tmp/tmp.ce
	ce /tmp/tmp.ce
