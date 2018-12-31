package com.example.api;

import com.example.bean.DataBean;
import com.example.flow.CouponRedemptionFlow;
import com.example.flow.IssueCouponAcceptanceFlow;
import com.example.flow.IssueCouponRequestFlow;
import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.ContractState;
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
    public Response getCouponStateQuery() {
        System.out.println("VaultQuery : " + rpcOps.vaultQuery(CouponState.class).getStates());
       return Response.status(200).entity(rpcOps.vaultQuery(CouponState.class).getStates()).build();
    }



    @GET
    @Path("approval-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApprovalData() {
        String emailId;
        int amount = 0;
        String couponName;
        UniqueIdentifier couponId = null;
        List<StateAndRef<CouponState>> input = rpcOps.vaultQuery(CouponState.class).getStates();
        emailId = input.get(0).getState().getData().getUsername();
        amount = input.get(0).getState().getData().getAmount();
        StringBuffer sbr = new StringBuffer();
        sbr.append("User assocaiated with coupo id is :  " + emailId + "<br/>" );
        sbr.append(" " + "Amount allocated is : " + amount + "<br/><br/>");
        sbr.append("<font color = Blue>Verification in progress ........ </font>");

        return Response.status(200).entity(sbr.toString()).build();
    }



    @GET
    @Path("vault-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAmount() {
        int amount = 0;
        String couponName;
        UniqueIdentifier couponId = null;
        List<StateAndRef<CouponState>> input = rpcOps.vaultQuery(CouponState.class).getStates();
        StringBuffer sbr = new StringBuffer();
        sbr.append("<TABLE BORDER=" + "\"" + 1 + "\"");
        sbr.append("CLASS=");
        sbr.append("   \"background\"           ");
        sbr.append("ID=output");
        sbr.append(">");

        sbr.append("<TR><TH>Coupon Name</TH><TH>Amount Remaining</TH><TH>Coupon id</TH></TR>");
        for (int i = 0; i < input.size(); i++) {

            amount = input.get(i).getState().getData().getAmount();
            couponName = input.get(i).getState().getData().getCouponName();
            couponId  = input.get(i).getState().getData().getCouponId();

            sbr.append("<TR><TD>" + couponName  + "</TD>");
            sbr.append("<TD>" + amount + "</TD>");
            sbr.append("<TD>" + couponId + "</TD>");
            sbr.append("</TR>");
        }

        sbr.append("</TABLE>");
        return Response.status(200).entity(sbr.toString()).build();
    }


    @POST
    @Path("coupon-generations")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response couponGeneration(DataBean dataBean) throws InterruptedException, ExecutionException {

        CordaX500Name couponVendorNode = dataBean.getPartyName();
        int value = dataBean.getValue();
        final Party otherParty = rpcOps.wellKnownPartyFromX500Name(couponVendorNode);
        String userName = dataBean.getUserName();
        String couponName = dataBean.getCouponName();

        if (userName == null || "".equalsIgnoreCase(userName) || userName.isEmpty()) {
            return Response.status(BAD_REQUEST).entity("paramete 'username' is invalid...!!!. \n").build();
        }
        if (couponName == null || "".equalsIgnoreCase(couponName) || couponName.isEmpty()) {
            return Response.status(BAD_REQUEST).entity("parameter 'couponName' is invalid...!!!. \n").build();
        }
        if (couponVendorNode == null) {
            return Response.status(BAD_REQUEST).entity("parameter 'partyName' missing or has wrong format.\n").build();
        }
        if (value <= 0) {
            return Response.status(BAD_REQUEST).entity(" parameter 'Amount' must be non-negative.\n").build();
        }
        if (otherParty == null) {
            return Response.status(BAD_REQUEST).entity("Party named " + couponVendorNode + "cannot be found.\n").build();
        }

        try {
            IssueCouponRequestFlow.Initiator initiator = new IssueCouponRequestFlow.Initiator(otherParty, value, userName, couponName);
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(initiator.getClass(), otherParty, value, userName, couponName)
                    .getReturnValue()
                    .get();

            final String msg = String.format(" Coupon for Transaction id %s  is successfully committed to ledger.\n ", signedTx.getId());
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
    public Response couponVerification(DataBean dataBean) throws InterruptedException, ExecutionException {

        String couponId = dataBean.getCouponId();

        if (couponId == null) {
            return Response.status(BAD_REQUEST).entity("linear id of previous unconsumed state cannot be empty. \n").build();
        }
        System.out.println("Vault Query Coupon State : " + rpcOps.vaultQuery(CouponState.class).getStates() );
        UniqueIdentifier linearIdCouponState = new UniqueIdentifier();
        UniqueIdentifier uuidCouponState = linearIdCouponState.copy(null, UUID.fromString(couponId));

        try {
            IssueCouponAcceptanceFlow.Initiator initiator = new IssueCouponAcceptanceFlow.Initiator(uuidCouponState);
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(initiator.getClass(), uuidCouponState)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Coupon Sucessfully Verified For Usage.\n Transaction id %s is successfully committed to ledger. \n ", signedTx.getId());
            return Response.status(CREATED).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    @POST
    @Path("coupon-redemption")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response couponRedemption(DataBean dataBean) throws InterruptedException, ExecutionException {

        String couponId = dataBean.getCouponId();
        int value = dataBean.getValue();
        String userName = dataBean.getUserName();

        if (userName == null || "".equalsIgnoreCase(userName) || userName.isEmpty()) {
            return Response.status(BAD_REQUEST).entity("paramete 'username' is invalid...!!!. \n").build();
        }
        if (couponId == null) {
            return Response.status(BAD_REQUEST).entity("couponId of previous unconsumed state cannot be empty. \n").build();
        }
        if (value <= 0) {
            return Response.status(BAD_REQUEST).entity(" parameter 'Amount' must be non-negative.\n").build();
        }

        System.out.println("Vault Query Coupon State : " + rpcOps.vaultQuery(CouponState.class).getStates());
        UniqueIdentifier linearIdCouponState = new UniqueIdentifier();
        UniqueIdentifier uuidCouponState = linearIdCouponState.copy(null, UUID.fromString(couponId));

        try {

            CouponRedemptionFlow.Initiator initiator = new CouponRedemptionFlow.Initiator(uuidCouponState, value, userName);
            final SignedTransaction signedTx = rpcOps
                                                .startTrackedFlowDynamic(initiator.getClass(), uuidCouponState, value, userName)
                                                .getReturnValue()
                                                .get();

            final String msg = String.format(" Congragulation. Coupon sucessfully Redeemed.\n Transaction id %s is successfully committed to ledger. \n ", signedTx.getId());
            return Response.status(BAD_REQUEST).entity(msg).build();
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
