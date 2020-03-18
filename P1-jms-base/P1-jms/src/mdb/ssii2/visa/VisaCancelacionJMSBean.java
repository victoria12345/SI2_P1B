/**
 * Pr&aacute;ctricas de Sistemas Inform&aacute;ticos II
 * VisaCancelacionJMSBean.java
 */

package ssii2.visa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenContext;
import javax.ejb.ActivationConfigProperty;
import javax.jms.MessageListener;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.JMSException;
import javax.annotation.Resource;
import java.util.logging.Logger;

/**
 * @author jaime
 */
@MessageDriven(mappedName = "jms/VisaPagosQueue")
public class VisaCancelacionJMSBean extends DBTester implements MessageListener {
  static final Logger logger = Logger.getLogger("VisaCancelacionJMSBean");
  @Resource
  private MessageDrivenContext mdc;

  private static final String UPDATE_CANCELA_QRY = "update pago set codRespuesta=999 where pago.idAutorizacion=?";
  private static final String UPDATE_RECTIFICA_QRY = "update tarjeta set saldo=? where tarjeta.numeroTarjeta=?";
  private static final String SELECT_TARJETA_QRY  = "select numeroTarjeta, importe from Pago where pago.idAutorizacion=?";
  private static final String SELECT_SALDO_QRY = "select slado from tarjeta where numeroTarjeta=?";


  public VisaCancelacionJMSBean() {
  }

  // TODO : Método onMessage de ejemplo
  // Modificarlo para ejecutar el UPDATE definido más arriba,
  // asignando el idAutorizacion a lo recibido por el mensaje
  // Para ello conecte a la BD, prepareStatement() y ejecute correctamente
  // la actualización
  public void onMessage(Message inMessage) {
      Connection pcon = null;
      PreparedStatement pstmt = null;
      TextMessage msg = null;
      ResultSet rs = null;
      double importe = 0;
      String tarjeta = "";
      double saldo = 0;

      try {

          pcon = getConnection();

          if (inMessage instanceof TextMessage) {
              msg = (TextMessage) inMessage;
              logger.info("MESSAGE BEAN: Message received: " + msg.getText());

              // ejecutamos el update del codRespuesta
              String qry = UPDATE_CANCELA_QRY;
              pstmt = pcon.prepareStatement(qry);
              String idAutorizacion = msg.getText();
              pstmt.setInt(1, Integer.parseInt(idAutorizacion));
              pstmt.executeUpdate();

              // Necesitamos numero de tarjeta para actualizar el saldo
              // tambien necesitamos el importe del pago que vamos a cancelar

              qry = SELECT_TARJETA_QRY;

              pstmt = pcon.prepareStatement(qry);
              pstmt.setInt(1, Integer.parseInt(idAutorizacion));
              rs = pstmt.executeQuery();

              if(rs.next()){
                tarjeta = rs.getString("numeroTarjeta");
                importe = rs.getDouble("importe");
              }else{
                throw new EJBException("no se ha podido realizar la cancelacion con ese idAutorizacion");
              }

              // obtenemos saldo del usuario tras el pago
              qry = SELECT_SALDO_QRY;
              pstmt = pcon.prepareStatement(qry);
              pstmt.setString(1, tarjeta);
              rs = pstmt.executeQuery();

              if(rs.next()){
                saldo = rs.getDouble("saldo");
              }else{
                throw new EJBException("Error al cancelar. No se ha podido obtener saldo para ese numero de tarjeta");
              }

              saldo = saldo+importe;

              //rectificamos el saldo tras la cancelacion del pago
              qry = UPDATE_RECTIFICA_QRY;
              pstmt = pcon.prepareStatement(qry);
              pstmt.setDouble(1, saldo);
              pstmt.setDouble(2, importe);
              pstmt.executeUpdate();

          } else {
              logger.warning(
                      "Message of wrong type: "
                      + inMessage.getClass().getName());
          }
      } catch (JMSException e) {
          e.printStackTrace();
          mdc.setRollbackOnly();
      } catch (Throwable te) {
          te.printStackTrace();
      }
  }


}
