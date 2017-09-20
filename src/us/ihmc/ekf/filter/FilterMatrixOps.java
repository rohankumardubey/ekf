package us.ihmc.ekf.filter;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.ops.CommonOps;

public class FilterMatrixOps
{
   private static final ThreadLocal<DenseMatrix64F> tempLocal1 = new ThreadLocal<DenseMatrix64F>()
   {
      @Override
      protected DenseMatrix64F initialValue()
      {
         return new DenseMatrix64F(0, 0);
      };
   };

   private static final ThreadLocal<DenseMatrix64F> tempLocal2 = new ThreadLocal<DenseMatrix64F>()
   {
      @Override
      protected DenseMatrix64F initialValue()
      {
         return new DenseMatrix64F(0, 0);
      };
   };

   private static final ThreadLocal<DenseMatrix64F> tempLocal3 = new ThreadLocal<DenseMatrix64F>()
   {
      @Override
      protected DenseMatrix64F initialValue()
      {
         return new DenseMatrix64F(0, 0);
      };
   };

   private static final ThreadLocal<LinearSolver<DenseMatrix64F>> solverLocal = new ThreadLocal<LinearSolver<DenseMatrix64F>>()
   {
      @Override
      protected LinearSolver<DenseMatrix64F> initialValue()
      {
         return LinearSolverFactory.linear(0);
      };
   };

   /**
    * Sets the provided matrix to a square identity matrix of the given size.
    *
    * @param matrix (modified)
    * @param size is the the desired number of rows and columns for the matrix
    */
   public static void setIdentity(DenseMatrix64F matrix, int size)
   {
      matrix.reshape(size, size);
      CommonOps.setIdentity(matrix);
   }

   /**
    * Sets the provided matrix to</br>
    * result = A * B * A'</br>
    * Note, that B must be square.
    */
   private static void computeABAtrans(DenseMatrix64F result, DenseMatrix64F A, DenseMatrix64F B)
   {
      DenseMatrix64F temp = tempLocal1.get();

      temp.reshape(B.getNumRows(), A.getNumRows());
      CommonOps.multTransB(B, A, temp);

      result.reshape(A.getNumRows(), A.getNumRows());
      CommonOps.mult(A, temp, result);
   }

   /**
    * Sets the provided matrix to</br>
    * result = A * B * A' + C
    */
   private static void computeABAtransPlusC(DenseMatrix64F result, DenseMatrix64F A, DenseMatrix64F B, DenseMatrix64F C)
   {
      computeABAtrans(result, A, B);
      CommonOps.add(result, C, result);
   }

   /**
    * Sets the provided matrix to</br>
    * result = inverse(A)</br>
    * Will return whether the inversion succeeded.
    *
    * @return whether the inversion succeeded
    */
   private static boolean invertMatrix(DenseMatrix64F result, DenseMatrix64F A)
   {
      LinearSolver<DenseMatrix64F> solver = solverLocal.get();
      DenseMatrix64F temp = tempLocal1.get();
      setIdentity(temp, A.getNumRows());

      if (!solver.setA(A))
      {
         CommonOps.fill(result, 0.0);
         return false;
      }

      result.reshape(A.getNumRows(), A.getNumCols());
      solver.solve(temp, result);
      return true;
   }

   /**
    * Sets the provided matrix to</br>
    * result = inverse(A * B * A' + C)</br>
    * Will return whether the inversion succeeded.
    *
    * @return whether the inversion succeeded
    */
   private static boolean computeInverseOfABAtransPlusC(DenseMatrix64F result, DenseMatrix64F A, DenseMatrix64F B, DenseMatrix64F C)
   {
      DenseMatrix64F temp = tempLocal2.get();
      computeABAtransPlusC(temp, A, B, C);
      return invertMatrix(result, temp);
   }

   /**
    * Sets the provided matrix to</br>
    * result = A * Pposterior * A' + Q
    *
    * @param result (modified)
    * @param A is the matrix that describes the linearized state evolution: x(k+1) = A * x(k)
    * @param Pposterior is the previous error covariance
    * @param Q is the covariance matrix of the state evolution
    */
   public static void predictErrorCovariance(DenseMatrix64F result, DenseMatrix64F A, DenseMatrix64F Pposterior, DenseMatrix64F Q)
   {
      computeABAtransPlusC(result, A, Pposterior, Q);
   }

   /**
    * Sets the provided matrix to</br>
    * result = P * H * inverse(H * P * H' + R)</br>
    * Will return whether the inversion succeeded.
    *
    * @return whether the inversion succeeded
    * @param result (modified)
    * @param P is the error covariance
    * @param H is the measurement jacobian
    * @param R is the measurement covariance
    */
   public static boolean computeKalmanGain(DenseMatrix64F result, DenseMatrix64F P, DenseMatrix64F H, DenseMatrix64F R)
   {
      DenseMatrix64F inv = tempLocal3.get();
      if (!computeInverseOfABAtransPlusC(inv, H, P, R))
      {
         CommonOps.fill(result, 0.0);
         return false;
      }

      DenseMatrix64F temp = tempLocal1.get();
      temp.reshape(P.getNumRows(), H.getNumRows());
      CommonOps.multTransB(P, H, temp);

      result.reshape(P.getNumRows(), R.getNumCols());
      CommonOps.mult(temp, inv, result);
      return true;
   }

   /**
    * Sets the provided matrix to</br>
    * result = xPrior + K * (z - H * xPrior)
    *
    * @param result (modified)
    * @param K is the kalman gain
    * @param z is the measurement
    * @param H is the measurement jacobian
    * @param xPrior is the state before the measurement update
    */
   public static void updateState(DenseMatrix64F result, DenseMatrix64F K, DenseMatrix64F z, DenseMatrix64F H, DenseMatrix64F xPrior)
   {
      DenseMatrix64F tempResidual = tempLocal1.get();
      DenseMatrix64F tempInnovation = tempLocal2.get();

      tempResidual.reshape(z.getNumRows(), 1);
      CommonOps.mult(H, xPrior, tempResidual);
      CommonOps.subtract(z, tempResidual, tempResidual);
      tempInnovation.reshape(xPrior.getNumRows(), 1);
      CommonOps.mult(K, tempResidual, tempInnovation);
      result.reshape(xPrior.getNumRows(), 1);
      CommonOps.add(xPrior, tempInnovation, result);
   }

   /**
    * Sets the provided matrix to</br>
    * result = (identity - K * H) * pPrior
    *
    * @param result (modified)
    * @param K is the kalman gain
    * @param H is the measurement jacobian
    * @param pPrior is the error covariance before the update
    */
   public static void updateErrorCovariance(DenseMatrix64F result, DenseMatrix64F K, DenseMatrix64F H, DenseMatrix64F pPrior)
   {
      DenseMatrix64F identity = tempLocal1.get();
      setIdentity(identity, pPrior.getNumRows());
      DenseMatrix64F temp = tempLocal2.get();

      temp.reshape(pPrior.getNumRows(), pPrior.getNumRows());
      CommonOps.mult(K, H, temp);

      CommonOps.subtract(identity, temp, temp);
      CommonOps.mult(temp, pPrior, result);
   }
}