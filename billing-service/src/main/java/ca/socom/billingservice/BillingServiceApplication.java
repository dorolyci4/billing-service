package ca.socom.billingservice;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.config.Projection;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

//import ca.socom.billingservice.CustomerService.ProductItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;




/*********************************/
@Entity
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor

class Bill  {
	@Id 
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private Date billingDate;
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long customerID;

	@Transient
	private Customer customer;
	
	@OneToMany(mappedBy = "bill")
	private Collection<ProductItem> productItems; //one to many
	

}

@RepositoryRestResource
interface BillRepository extends JpaRepository<Bill, Long>{
	
}

// Ajout d<une projection de la facture
@Projection(name = "fullBill",types = Bill.class)
interface BillProjection{
	public Long getId();
	public Date getBillingDate();
	public Long getCustomerID();
	public Collection<ProductItem> getProductItems();
	

}



@Entity
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
class ProductItem{
	@Id 
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long productID; // concerne un produit  - Many to One, l entity product est persistant dans un autre service 
	                        //c est une cle etrangere. On va utiliser une classe locale pour comleter le modele.
	@Transient
	private Product product; // pas persistant au niveau BD pas prise en consideration
//	private String name;
	private Double price;
	private Double quantity;
	@ManyToOne
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Bill bill; // un productItem concerne une facture
	                   // association one to many et many to one
}

@RepositoryRestResource
interface ProductItemRepository extends JpaRepository<ProductItem, Long>{
	
}

/***************Customer Service********************/

@Data
class Customer{
	private Long id; private String name; private String email; 
}

@FeignClient(name="CUSTOMER-SERVICE")  // chercher des infos de l autre service
interface CustomerService{
	@GetMapping("/customers/{id}")
	public Customer findCustomerById(@PathVariable(name="id") Long id);
}
//**************************************** Equivalent de Spring Data Rest - interface


/***************Inventory Service********************/

@Data
class Product{
	private Long id; private String name; private double price; 
}

@FeignClient(name="INVENTORY-SERVICE")  // chercher des infos de l autre service
interface InventoryService{
	@GetMapping("/products/{id}")
	public Product findProductById(@PathVariable(name="id") Long id);
	
	@GetMapping("/products")       
	public PagedModel <Product> findAllproducts();   // Tous produsts de maniere delarative
	                                                // pas marcher car on une liste
	                                                // il faut creer une classe, mais nous utiliser HATEOAS pour deserialiser
	                                                // Collection<Product> replacer par PagedModel <Product> 
}
//**************************************** Equivalent de Spring Data Rest - interface

//*********************************

@SpringBootApplication
@EnableFeignClients
public class BillingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingServiceApplication.class, args);
	}
	
	//insertion de donnee au denarrage
	// on va injecter BillRepository et ProductItemRepository
	@Bean
	CommandLineRunner start(BillRepository billRepository, 
			ProductItemRepository productItemRepository,
			CustomerService customerService, InventoryService inventoryService) {
		return args->{
			
			Customer c1=customerService.findCustomerById(1L);
			System.out.println("***************");
			System.out.println("ID= "+c1.getId());
			System.out.println("NAME= "+c1.getName());
			System.out.println("EMAIL= "+c1.getEmail());
			
			System.out.println("***************");
			// creer un facture
			
			Bill b1=billRepository.save(new Bill(null,new Date(),c1.getId(),null,null));
			
			PagedModel<Product> products = inventoryService.findAllproducts(); // all products
			
			products.getContent().forEach(p->{
				 productItemRepository.save(new ProductItem(null, p.getId(),null, p.getPrice(),11.00, b1));
			});
			
			
		};
	}

}


//Creation d'un controller
@RestController
class BillRestController{
	@Autowired
	private  BillRepository billRepository;  //springData
	@Autowired
	private ProductItemRepository productItemRepository;
	@Autowired
	private CustomerService customerService;
	
	@Autowired
	InventoryService inventoryService;
	
	@GetMapping("/fullBill/{id}")
	public Bill getBill(@PathVariable(name = "id") Long id) {
		Bill bill=billRepository.findById(id).get();
		//Ajouter l'ID du client
		
		bill.setCustomer(customerService.findCustomerById(bill.getCustomerID()));
		
		//Ajouter les produits
		bill.getProductItems().forEach(pi->{
			pi.setProduct(inventoryService.findProductById(pi.getProductID())); // pi : product item
		});
		return bill;  // facture qui contient le detail
	}
}

