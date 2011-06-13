
CP=./lib/jade.jar:./lib/common-codecs/commons-codec-1.3.jar:./classes:../../../tmp/add-ons/xmlacl/lib/xmlacl.jar:../../../tmp/add-ons/xmlacl/lib/sax2/sax2.jar:../../../tmp/add-ons/XMLCodec/lib/XMLCodec.jar
BOOT=jade.Boot
JADE=java -ea -cp $(CP) -Xdebug -Xrunjdwp:transport=dt_socket,address=jdbconn,server=y,suspend=n $(BOOT) 
REMOTE=-host localhost -port 1099 -container
AGENTCP=src.coop
NAME=bifab




#LAUNCH=Monitor:$(AGENTCP).MonitorAgent;Workplace0:$(AGENTCP).WorkplaceAgent

.PHONY: insert build gui
insert: build
	$(JADE) $(REMOTE) "Storage:$(AGENTCP).StorageAgent; \
	Logistic0:$(AGENTCP).LogisticAgent; \
	Logistic1:$(AGENTCP).LogisticAgent; \
	Logistic2:$(AGENTCP).LogisticAgent; \
	WorkplacePurchase:$(AGENTCP).WorkplaceAgent(purchase); \
	WorkplaceFahrrad:$(AGENTCP).WorkplaceAgent(fahrrad); \
	WorkplaceReifen:$(AGENTCP).WorkplaceAgent(reifen); \
	Monitor:$(AGENTCP).MonitorAgent" | tee test.log

kill:
	ps aux | grep jade | awk ' { system("kill " $$2); }'


#	$(JADE) $(REMOTE) Workplace1:WorkplaceAgent
#	$(JADE) $(REMOTE) Workplace2:WorkplaceAgent
#	$(JADE) $(REMOTE) Purchase:WorkplaceAgent
#	$(JADE) $(REMOTE) Storage:StoareAgent
#	$(JADE) $(REMOTE) Logistic0:LogisticAgent
#	$(JADE) $(REMOTE) Logistic1:LogisticAgent


maincontainer: build
	$(JADE) -name $(NAME) -gui

build:
	ant jade