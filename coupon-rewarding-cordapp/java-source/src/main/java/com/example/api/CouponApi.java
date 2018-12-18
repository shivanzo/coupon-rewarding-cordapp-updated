package com.example.api;

import com.example.bean.DataBean;
import com.example.flow.CouponIssuanceFlow;
import com.example.flow.CouponVerificationFlow;
import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;

@Path("rewards")
public class CouponApi {

    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;
    private final List<String> serviceNames = ImmutableList.of("Notary");

    static private final Logger logger = LoggerFactory.getLogger(CouponApi.class);

    public CouponApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
    }
    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, CordaX500Name> whoami() {
        return ImmutableMap.of("me", myLegalName);
    }

    @GET
    @Path("coupon-states")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFinacneBankQuery() {
        System.out.println("VaultQuery : " + rpcOps.vaultQuery(CouponState.class).getStates());
       return Response.status(200).entity(rpcOps.vaultQuery(CouponState.class).getStates()).build();
    }

    @POST
    @Path("coupon-generations")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response loanRequest(DataBean detail) throws InterruptedException, ExecutionException {

        CordaX500Name amazonNode = detail.getPartyName();
        int value = detail.getValue();
        final Party otherParty = rpcOps.wellKnownPartyFromX500Name(amazonNode);

        if (amazonNode == null) {
            return Response.status(BAD_REQUEST).entity("parameter 'partyName' missing or has wrong format.\n").build();
        }

        if (value <= 0) {
            return Response.status(BAD_REQUEST).entity(" parameter 'Amount' must be non-negative.\n").build();
        }

        if (otherParty == null) {
            return Response.status(BAD_REQUEST).entity("Party named " + amazonNode + "cannot be found.\n").build();
        }

        try {
            CouponIssuanceFlow.Initiator initiator = new CouponIssuanceFlow.Initiator(otherParty, value);
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(initiator.getClass(), otherParty, value)
                    .getReturnValue()
                    .get();

            System.out.println("couponId : " + initiator.getCouponId());
            final String msg = String.format("NETMEDS India. \n Congragulations..!! Coupon for Transaction id %s  is successfully committed to ledger.\n ", signedTx.getId());
            return Response.status(CREATED).entity(msg).build();
        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    @POST
    @Path("coupon-verifications")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response creditAgencyResponse(DataBean dataBean) throws InterruptedException, ExecutionException {

        CordaX500Name partyName = dataBean.getPartyName();
        String couponId = dataBean.getCouponId();

        final Party otherParty = rpcOps.wellKnownPartyFromX500Name(partyName);

        if (partyName == null) {
            return Response.status(BAD_REQUEST).entity(" parameter 'partyName' missing or has wrong format.\n").build();
        }

        if (otherParty == null) {
            return Response.status(BAD_REQUEST).entity("Party named " + partyName + "cannot be found.\n").build();
        }

        if (couponId == null) {
            return Response.status(BAD_REQUEST).entity("linear id of previous unconsumed state cannot be empty. \n").build();
        }

        System.out.println("Vault Query Coupon State : " + rpcOps.vaultQuery(CouponState.class).getStates() );
        UniqueIdentifier linearIdCouponState = new UniqueIdentifier();
        UniqueIdentifier uuidCouponState = linearIdCouponState.copy(null, UUID.fromString(couponId));

        try {
            //CreditRatingResponseFlow.Initiator initiator = new CreditRatingResponseFlow.Initiator(otherParty, uuidCouponState);
            CouponVerificationFlow.Initiator initiator = new CouponVerificationFlow.Initiator(otherParty, uuidCouponState);
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(initiator.getClass(), otherParty, uuidCouponState)
                    .getReturnValue()
                    .get();

            final String msg = String.format("AMAZON INDIA.\n Transaction id %s is successfully committed to ledger. \n", signedTx.getId());
            return Response.status(CREATED).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getPeers() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("peers", nodeInfoSnapshot.stream().map(node -> node.getLegalIdentities().get(0).getName())
                .filter(name -> !name.equals(myLegalName) && !serviceNames.contains(name.getOrganisation()))
                .collect(toList()));
    }

    @GET
    @Path("states")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<CouponState>> getStatesFromVault() {
        return rpcOps.vaultQuery(CouponState.class).getStates();
    }
}
