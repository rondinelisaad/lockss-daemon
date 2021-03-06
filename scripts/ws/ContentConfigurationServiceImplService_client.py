##################################################
# file: ContentConfigurationServiceImplService_client.py
# 
# client stubs generated by "ZSI.generate.wsdl2python.WriteServiceModule"
#     /usr/bin/wsdl2py -b ContentConfigurationService.wsdl
# 
##################################################

from ContentConfigurationServiceImplService_types import *
import urlparse, types
from ZSI.TCcompound import ComplexType, Struct
from ZSI import client
from ZSI.schema import GED, GTD
import ZSI
from ZSI.generate.pyclass import pyclass_type

# Locator
class ContentConfigurationServiceImplServiceLocator:
    ContentConfigurationServiceImplPort_address = "http://localhost:8081/ws/ContentConfigurationService"
    def getContentConfigurationServiceImplPortAddress(self):
        return ContentConfigurationServiceImplServiceLocator.ContentConfigurationServiceImplPort_address
    def getContentConfigurationServiceImplPort(self, url=None, **kw):
        return ContentConfigurationServiceImplServiceSoapBindingSOAP(url or ContentConfigurationServiceImplServiceLocator.ContentConfigurationServiceImplPort_address, **kw)

# Methods
class ContentConfigurationServiceImplServiceSoapBindingSOAP:
    def __init__(self, url, **kw):
        kw.setdefault("readerclass", None)
        kw.setdefault("writerclass", None)
        # no resource properties
        self.binding = client.Binding(url=url, **kw)
        # no ws-addressing

    # op: reactivateAusByIdList
    def reactivateAusByIdList(self, request, **kw):
        if isinstance(request, reactivateAusByIdList) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(reactivateAusByIdListResponse.typecode)
        return response

    # op: deleteAuById
    def deleteAuById(self, request, **kw):
        if isinstance(request, deleteAuById) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(deleteAuByIdResponse.typecode)
        return response

    # op: deleteAusByIdList
    def deleteAusByIdList(self, request, **kw):
        if isinstance(request, deleteAusByIdList) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(deleteAusByIdListResponse.typecode)
        return response

    # op: addAuById
    def addAuById(self, request, **kw):
        if isinstance(request, addAuById) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(addAuByIdResponse.typecode)
        return response

    # op: reactivateAuById
    def reactivateAuById(self, request, **kw):
        if isinstance(request, reactivateAuById) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(reactivateAuByIdResponse.typecode)
        return response

    # op: addAusByIdList
    def addAusByIdList(self, request, **kw):
        if isinstance(request, addAusByIdList) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(addAusByIdListResponse.typecode)
        return response

    # op: deactivateAuById
    def deactivateAuById(self, request, **kw):
        if isinstance(request, deactivateAuById) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(deactivateAuByIdResponse.typecode)
        return response

    # op: deactivateAusByIdList
    def deactivateAusByIdList(self, request, **kw):
        if isinstance(request, deactivateAusByIdList) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(deactivateAusByIdListResponse.typecode)
        return response

reactivateAusByIdList = GED("http://content.ws.lockss.org/", "reactivateAusByIdList").pyclass

reactivateAusByIdListResponse = GED("http://content.ws.lockss.org/", "reactivateAusByIdListResponse").pyclass

deleteAuById = GED("http://content.ws.lockss.org/", "deleteAuById").pyclass

deleteAuByIdResponse = GED("http://content.ws.lockss.org/", "deleteAuByIdResponse").pyclass

deleteAusByIdList = GED("http://content.ws.lockss.org/", "deleteAusByIdList").pyclass

deleteAusByIdListResponse = GED("http://content.ws.lockss.org/", "deleteAusByIdListResponse").pyclass

addAuById = GED("http://content.ws.lockss.org/", "addAuById").pyclass

addAuByIdResponse = GED("http://content.ws.lockss.org/", "addAuByIdResponse").pyclass

reactivateAuById = GED("http://content.ws.lockss.org/", "reactivateAuById").pyclass

reactivateAuByIdResponse = GED("http://content.ws.lockss.org/", "reactivateAuByIdResponse").pyclass

addAusByIdList = GED("http://content.ws.lockss.org/", "addAusByIdList").pyclass

addAusByIdListResponse = GED("http://content.ws.lockss.org/", "addAusByIdListResponse").pyclass

deactivateAuById = GED("http://content.ws.lockss.org/", "deactivateAuById").pyclass

deactivateAuByIdResponse = GED("http://content.ws.lockss.org/", "deactivateAuByIdResponse").pyclass

deactivateAusByIdList = GED("http://content.ws.lockss.org/", "deactivateAusByIdList").pyclass

deactivateAusByIdListResponse = GED("http://content.ws.lockss.org/", "deactivateAusByIdListResponse").pyclass
